# Seeded demonstration deployment

The repository includes a Render Blueprint in `render.yaml`. It declares managed PostgreSQL and separate API/frontend services, with `DEMO_SEED=true` and `DEMO_MODE=true`. The specified persistent database and always-on services are paid plans; importing the Blueprint is a separate deployment decision. No Render resources were created or changed during this implementation.

## Configure a new deployment

1. Import this Git repository as a Render Blueprint and review the service plans before applying it.
2. Set `BOOTSTRAP_PASSWORD` on the `hospital-ward-backend` service before its first deploy to an empty database. It must be at least 12 characters and at most 72 UTF-8 bytes; the backend needs it to create the initial administrator. A new Blueprint prompts for this secret during its first setup, as described in [Render's Blueprint documentation](https://render.com/docs/blueprint-spec#prompting-for-secret-values). When adding it to an existing Blueprint, set it manually in the backend service's environment settings because Render does not prompt again when syncing a new `sync: false` variable. On a fresh database, sign in as `admin` with this value; a demo reset uses the configured value again. Also supply `RESEND_API_KEY` privately on the API service. Keep both secrets out of Git, Docker build arguments, and frontend environment variables.
3. Set `PUBLIC_APP_URL` on both services to the actual public HTTPS frontend origin, without a trailing path. This controls email links and social metadata. Rebuild the frontend after changing its public origin or API destination.
4. The Blueprint wires PostgreSQL host, port, database, user and password from the database resource. `DATABASE_URL`, when present, takes precedence and must be a JDBC PostgreSQL URL. Remove old H2 `/tmp` overrides before switching an existing service.
5. Keep `COOKIE_SECURE=true` behind HTTPS. The frontend proxies same-origin `/api` requests; no browser CORS exception is needed.
6. Confirm `/api/v1/health/ready` returns `UP`, then enter the administrator demo. Verify patient count, active admissions, room capacity, procedure reports and planner simulation before presenting.

## Health probes

The backend exposes two unauthenticated probes. Neither returns hostnames, credentials or health details beyond an `UP`/`DOWN` state.

| Probe | Path | Checks | Use it for |
| --- | --- | --- | --- |
| Liveness | `/api/v1/health/live` | The process can serve HTTP | Restart decisions (container orchestrators) |
| Readiness | `/api/v1/health/ready` | Liveness, a database round trip, and no pending or failed Flyway migration | Traffic routing, Compose `service_healthy`, Render `healthCheckPath` |

Readiness reports `database` (`UP`/`UNAVAILABLE`) and `migrations` (`UP`/`PENDING`/`UNKNOWN`) without schema versions. A live but not-ready backend (for example, waiting on PostgreSQL) answers `200` on liveness and `503` on readiness, so do not restart it on a readiness failure. `/api/v1/health` remains as an alias of readiness for existing probes. Compose and `render.yaml` use readiness.

## Graceful shutdown

On `SIGTERM` the backend stops accepting new connections, readiness switches to `503` (`"traffic": "REFUSING"`), open operations streams are closed so clients reconnect elsewhere, and in-flight requests get up to `SHUTDOWN_GRACE_PERIOD` (default `20s`) to finish. Scheduled notification jobs get a further 10 seconds. Each request runs in a single database transaction: work that finishes within the grace period commits; work still running afterwards is aborted and PostgreSQL rolls back its open transaction when the connection closes, so no partial admission, transfer or discharge is left behind.

Keep the platform's kill timeout above the grace period. Compose sets `stop_grace_period: 30s`; Render sends `SIGTERM` and waits 30 seconds by default, which also fits.

## Request and database timeouts

| Variable | Default | Bounds |
| --- | --- | --- |
| `DATABASE_CONNECT_TIMEOUT_SECONDS` | `10` | Opening a new TCP connection to PostgreSQL |
| `DATABASE_POOL_TIMEOUT_MS` | `5000` | Waiting for a free pooled connection |
| `DATABASE_STATEMENT_TIMEOUT_MS` | `15000` | Any single SQL statement (server-side `statement_timeout`) |
| `API_TRANSACTION_TIMEOUT` | `30s` | All database work of one API request |

A cancelled statement or expired transaction rolls back and returns `503` with code `DATABASE_TIMEOUT` and `Retry-After: 5`; nothing from that request is saved. An unreachable database or exhausted pool returns `503` with `DATABASE_UNAVAILABLE`. Flyway migrations share the statement timeout, so raise `DATABASE_STATEMENT_TIMEOUT_MS` for the release that runs a long data migration. The external AI adapter keeps its own `app.ai.timeout-seconds`.

## Connection-pool sizing

Each backend instance opens at most `DATABASE_POOL_SIZE` connections (default `10`, keeping `DATABASE_POOL_MIN_IDLE`, default `2`, open when idle). Size it so that:

```
instances × DATABASE_POOL_SIZE + migration/admin headroom (≈5) ≤ PostgreSQL max_connections
```

For example, a database allowing 97 connections (a common small managed plan) fits up to 9 instances at the default size. Two instances on a 25-connection plan should use `DATABASE_POOL_SIZE=10` at most. A larger pool rarely helps: requests are short transactions, so raise instances before pool size and keep the pool near `2 × CPU cores` of the database.

Watch saturation through the administrator-only metrics endpoint (pool tag `hospital-db`):

| Metric | Meaning | Act when |
| --- | --- | --- |
| `/api/v1/management/metrics/hikaricp.connections.active` | Connections in use | Close to `hikaricp.connections.max` for minutes |
| `/api/v1/management/metrics/hikaricp.connections.pending` | Requests waiting for a connection | Above zero for more than brief bursts |
| `/api/v1/management/metrics/hikaricp.connections.timeout` | Waits that failed (`DATABASE_UNAVAILABLE`) | Any increase |
| `/api/v1/management/metrics/hikaricp.connections.acquire` | Time to obtain a connection | Rising percentiles |

Sustained pending connections mean too many instances for the database or slow statements; check slow queries before enlarging the pool.

## Demo scenario and reset

An empty database is seeded once with 28 synthetic patients, 14 active and 12 discharged admissions, rooms with varied occupancy, one inactive room, procedure history, transfer history, expected discharge dates and audit events. Dates are relative to the seed/reset instant. Persistent databases keep their dates and user changes across restarts. To refresh the story for a presentation, use **Reset demonstration**, enter `RESET DEMO`, and sign in again.

Reset is deliberately destructive inside the dedicated demo: it deletes all accounts and operational records, clears proposals and verification tokens, and recreates the scenario in a transaction. IDs are not reused; old sessions cannot inherit newly seeded accounts. Do not enable `DEMO_MODE` on a database containing real patient data or accounts that must be retained. To retain registered accounts, disable demo reset by setting `DEMO_MODE=false`; ordinary sign-in and registration remain available.

An existing database is never silently reseeded. Back it up before changing database configuration. Pointing at a new PostgreSQL database does not migrate old H2 records.

## Email registration

Configuration: `REGISTRATION_ENABLED=true`, `RESEND_API_KEY`, `EMAIL_FROM`, `PUBLIC_APP_URL`. Optional `EMAIL_CONFIRMATION_MINUTES` defaults to 30. The HTML template is `backend/src/main/resources/emails/confirm-account.html`, with a plain-text alternative.

The requested sender is `Medcore <onboarding@resend.dev>`. Resend restricts this testing sender to the email associated with the Resend account. General public registration requires a verified sending domain and an updated `EMAIL_FROM`; see [Resend's documented restriction](https://resend.com/docs/knowledge-base/403-error-resend-dev-domain). A rejected delivery rolls back a new registration, and the UI reports a delivery error rather than pretending an email was sent.

All registrations begin as disabled PATIENT accounts linked to newly created patient records. Email verification activates the patient account. Doctor requests remain PATIENT until an administrator creates/selects an active doctor profile and updates the verified account's role and doctor link in Team access. Registering never claims a pre-existing patient identity by matching a name or birth date.

## Pre-presentation check

Open the frontend and its proxied health endpoint ahead of time, reset the dedicated demo if desired, then verify one transfer and its audit event. Keep the presentation route open to show automatic refresh. Its QR code uses the current origin; a localhost QR requires a phone-reachable deployment instead.

The wake screen retries service health and offers a manual retry after a prolonged cold start. It is not a substitute for an available hosting plan.

The public URL must be taken from the deployed service; it is not invented in this repository. The Render connector returned unauthorized in this session, and the project owner requested commit/push only.
