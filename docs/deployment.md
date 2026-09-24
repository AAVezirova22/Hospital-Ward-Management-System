# Seeded demonstration deployment

The repository includes a Render Blueprint in `render.yaml`. It declares managed PostgreSQL and separate API/frontend services, with `DEMO_SEED=true`, `DEMO_MODE=true`, and `DEMO_PUBLIC_LOGIN=true` on the dedicated demo API. The specified persistent database and always-on services are paid plans; importing the Blueprint is a separate deployment decision. No Render resources were created or changed during this implementation.
The repository includes a Render Blueprint in `render.yaml`. It declares managed PostgreSQL and separate API/frontend services, with `DEMO_SEED=true` and `DEMO_MODE=true`. The database uses paid plan `0.1c-256mb`; both web services use paid plan `0.5c-512mb`. Review [current Render pricing](https://render.com/pricing) before creating or syncing the Blueprint; syncing these plans to existing free resources changes them to paid tiers and starts paid usage. No Render resources were created or changed for this repository update.

The paid database avoids Free Postgres's 30-day expiration and keeps its data across web-service deploys and restarts while the database resource exists. Deleting or replacing the database can remove data. Render's point-in-time recovery window is 3 days on Hobby and 7 days on Pro or higher; logical backup exports are retained for 7 days. Download backups elsewhere for longer retention and back up before changing database configuration ([Render Postgres backups](https://render.com/docs/postgresql-backups), [Free plan limits](https://render.com/docs/free)). See [database recovery objectives](recovery-objectives.md) for proposed targets and the required backup cadence.

The paid web services do not spin down after 15 minutes of inactivity, unlike Free web services. Deploys and platform maintenance can still restart them, so this avoids free-tier idle cold starts without promising uninterrupted availability. The Blueprint does not attach persistent disks to the web services, so their filesystems are ephemeral; durable operational records belong in Postgres. Assistant uploads and extracted source text are not persisted. The database keeps only the bounded, expiring assistant context described below.

## Configure a new deployment

1. Import this Git repository as a Render Blueprint. Review the paid tiers and current pricing before applying it.
2. Supply `RESEND_API_KEY` privately on the API service. Never put it in Git, a Docker build argument, or a frontend environment variable.
3. Set `PUBLIC_APP_URL` on both services to the actual public HTTPS frontend origin, without a trailing path. This controls email links and social metadata. Rebuild the frontend after changing its public origin or API destination.
4. The Blueprint wires PostgreSQL host, port, database, user and password from the database resource. `DATABASE_URL`, when present, takes precedence and must be a JDBC PostgreSQL URL. Remove old H2 `/tmp` overrides before switching an existing service.
5. Keep `COOKIE_SECURE=true` behind HTTPS. The frontend proxies same-origin `/api` requests; no browser CORS exception is needed.
6. Confirm `/api/v1/health/ready` returns `UP`, then enter the administrator demo. Verify patient count, active admissions, room capacity, procedure reports and planner simulation before presenting.

## Startup configuration check

The backend refuses to start when deployment settings are missing or contradict each other, and lists every problem at once by variable name. Values are never printed, so the log is safe to share. It checks:

- `DATABASE_PASSWORD` is set (and not the old committed fallback); `DATABASE_URL`, when used, is a `jdbc:postgresql:` URL.
- `BOOTSTRAP_PASSWORD`, when set, is 12 characters to 72 UTF-8 bytes.
- `PUBLIC_APP_URL`, when set, is a bare `http(s)` origin; an `https` origin requires `COOKIE_SECURE=true`.
- `RESEND_API_KEY` requires `EMAIL_FROM` and `PUBLIC_APP_URL`. `EMAIL_FROM` alone just leaves email off.
- `EMAIL_CONFIRMATION_MINUTES` is between 5 and 1440.
- `AI_MODE` is `local`, `external` or `off`; `external` needs an `http(s)` `AI_URL` and an `AI_MODEL`.
- `APP_ENVIRONMENT` is `development` (default), `demo`, `staging` or `production`. `DEMO_MODE=true` (destructive reset) is only accepted with `APP_ENVIRONMENT=demo`, and `APP_ENVIRONMENT=production` requires `DEMO_SEED=false`. The label is returned by `/api/v1/demo/status` so the interface can show which environment it is using. `render.yaml` and CI declare `demo`; a real deployment should set `production` with `DEMO_SEED=false` and `DEMO_MODE=false`.

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

## Audit integrity

`audit_events` is append-only for the application. A database trigger rejects every `UPDATE`, `DELETE` and `TRUNCATE` on the table, including SQL sent by the backend's own database role, and the entity is read-only in Hibernate. New events are still inserted normally.

One controlled path may remove history: the synthetic demo reset (`DEMO_MODE=true`, `APP_ENVIRONMENT=demo`). It opts in for its own transaction only, with `select set_config('hospital.audit_maintenance', 'on', true)`. The setting ends with that transaction and cannot leak into pooled connections.

Limits of this protection:

- It stops accidental ORM updates, buggy or injected SQL through the application, and casual edits with the application credentials. It is not cryptographic tamper evidence.
- Whoever can alter the schema can bypass it. Flyway runs as the application role, which owns the table and could drop the trigger or set the maintenance flag. For stronger separation, run migrations with a dedicated owner role, give the runtime role only `SELECT, INSERT` on `audit_events`, and keep superuser credentials out of the application.
- Copy audit events to storage the database operators cannot rewrite (log shipping, write-once object storage) if records must stand up to an administrator with database access.
- Backups contain the same rows. Protect and retain them according to [Database backup security](database-backup-security.md) and your organisation's retention rules.

## Slow-query diagnostics

Every JDBC statement is timed. Statements taking `SLOW_QUERY_MS` or longer (default `500`; `0` turns the log off) are logged at `WARN` on the `hospital.slow-query` logger:

```
Slow select statement took 812 ms: select a.id, a.status from admissions a where a.department_id = ? and a.status = ?
```

The label never contains data: bound parameters are not read, and string and number literals in the SQL text are replaced with `?`. `IN` lists collapse to `(?...)`. Use the label to find the query in the code, then run `EXPLAIN ANALYZE` with representative synthetic values.

Aggregate timings are available to administrators at `/api/v1/management/metrics/hospital.db.statements`, tagged by `operation` (`select`, `insert`, `update`, `delete`, `with`, `other`) and `outcome` (`success`, `error`). Compare them with `hikaricp.connections.pending`: slow statements usually cause pool waits, not the other way round.

## Multiple backend instances

The supported multi-instance setup requires all API instances to use the same PostgreSQL database and cookie-based session affinity. Spring Security stores authenticated sessions in each servlet container's memory; configure the load balancer to route requests with the same `JSESSIONID` cookie to the same API instance. Preserve and forward that cookie through the frontend `/api` proxy, including login, CSRF, assistant upload, message, source-removal and clear requests. The servlet session expires after 30 minutes of inactivity, so affinity must last at least as long as the active session.

Assistant conversation turns, uploaded source text, the one-in-flight request guard and login-failure backoff are also held in process memory. Routing a browser session to another instance or restarting its instance loses its login and transient assistant context. The user can sign in again, but the earlier assistant context and uploads cannot be resumed. The AI session record, pending actions and rate-limit windows are stored in PostgreSQL and shared across instances. The one-in-flight guard is per instance, so separate browser sessions for the same account can make simultaneous requests on different instances. Run one API instance if the deployment cannot preserve cookie affinity or needs a global in-flight limit.
## Assistant context retention

When an external AI model is configured, PostgreSQL stores up to six recent user/assistant text pairs per assistant session, with a 40,000-character cap and a rolling 30-minute expiry. The `ai_sessions` row scopes that context to its owner and department; clearing the session removes it, and a scheduled cleanup scrubs expired text. These pairs can contain patient information, so restrict database and backup access accordingly. Uploaded files, extracted source text and raw tool results remain in process memory or are discarded after the request.

## Multiple backend instances

All API instances must use the same PostgreSQL database. Assistant conversation context and rate-limit windows are shared there, but Spring Security's servlet session, uploaded source text, the one-in-flight assistant guard and login-failure backoff remain in each instance's memory. Configure load-balancer affinity by the `JSESSIONID` cookie and preserve that cookie through the frontend `/api` proxy so a browser keeps its login, CSRF state and uploaded sources on one API instance. The servlet session expires after 30 minutes of inactivity; affinity must cover the active session. A backend restart or failover loses the login, in-flight guard and uploaded sources, while the persisted recent conversation context remains available. The one-in-flight guard is per instance, so separate sessions for the same account can still make simultaneous requests on different instances. Run one API instance if the deployment cannot preserve cookie affinity or needs a global in-flight limit.

## Public demo entry

`DEMO_PUBLIC_LOGIN` defaults to `false`. The Render Blueprint enables it only for the API connected to the dedicated synthetic demo database. This explicitly allows the browser's demo role buttons to sign in through the frontend's private `/api` proxy, whose backend connection does not appear to come from loopback. The server does not trust `X-Forwarded-For` for this decision. Demo mode and seeded demo identities are still required.

If `DEMO_TOKEN` is configured, the token is required even when public login is enabled; keep it on the backend and never expose it to the browser. For other deployments, leave `DEMO_PUBLIC_LOGIN=false` or configure a private token. Do not enable public demo login on a database containing real patient data or user accounts.

## Demo scenario and reset

An empty database is seeded once with 28 synthetic patients, 14 active and 12 discharged admissions, rooms with varied occupancy, one inactive room, procedure history, transfer history, expected discharge dates and audit events. Dates are relative to the seed/reset instant. Persistent databases keep their dates and user changes across restarts. To refresh the story for a presentation, use **Reset demonstration**, enter `RESET DEMO`, and sign in again.

Reset is deliberately destructive inside the dedicated demo: it deletes all accounts and operational records, clears proposals and verification tokens, and recreates the scenario in a transaction. IDs are not reused; old sessions cannot inherit newly seeded accounts. Do not enable `DEMO_MODE` on a database containing real patient data or accounts that must be retained. To retain registered accounts, disable demo reset by setting `DEMO_MODE=false`; ordinary sign-in and registration remain available.

An existing database is never silently reseeded. Back it up before changing database configuration. Pointing at a new PostgreSQL database does not migrate old H2 records. Follow the [database backup security and recovery runbook](database-backup-security.md) to protect independent copies, separate database roles and verify restores.

## Email registration

Configuration: `REGISTRATION_ENABLED=true`, `RESEND_API_KEY`, `EMAIL_FROM`, `PUBLIC_APP_URL`. Optional `EMAIL_CONFIRMATION_MINUTES` defaults to 30. The HTML template is `backend/src/main/resources/emails/confirm-account.html`, with a plain-text alternative.

The requested sender is `Medcore <onboarding@resend.dev>`. Resend restricts this testing sender to the email associated with the Resend account. General public registration requires a verified sending domain and an updated `EMAIL_FROM`; see [Resend's documented restriction](https://resend.com/docs/knowledge-base/403-error-resend-dev-domain).

Signup, resend and recovery write the account changes, hashed verification link and delivery request in one database transaction. A background worker claims queued messages across backend instances, sends outside the signup transaction and retries temporary provider failures with bounded backoff. The same confirmation token is used for retries, with a stable provider idempotency key. A delivery failure therefore leaves the account disabled and its confirmation request queued; it does not discard the account. The default worker polls every five seconds and claims up to ten messages (`EMAIL_OUTBOX_POLL_INTERVAL_MS`, `EMAIL_OUTBOX_INITIAL_DELAY_MS` and `EMAIL_OUTBOX_BATCH_SIZE` tune this behavior).

The outbox holds the one-time confirmation token, recipient and first name while delivery is pending, for at most the configured confirmation lifetime. It clears those values after delivery, replacement, verification or expiry. Terminal delivery records are retained for 30 days. Restrict direct database access and protect database backups while pending messages may contain these secrets.

## Upcoming discharge reminders

Reminders are disabled by default. To enable them, set `DISCHARGE_REMINDERS_ENABLED=true` after configuring `RESEND_API_KEY` and a valid `EMAIL_FROM`, and make sure the department's assigned doctors and team staff have enabled accounts with verified email addresses. The reminder sender needs the Resend key and sender address; it does not require `PUBLIC_APP_URL`.

`DISCHARGE_REMINDER_WINDOWS` accepts unique day counts from 1 to 365 and defaults to `7,3,1`. The polling interval defaults to five minutes. Failed provider requests retry after the configured `DISCHARGE_REMINDER_RETRY_DELAY_MS`, up to `DISCHARGE_REMINDER_MAX_ATTEMPTS` (default 5); `DISCHARGE_REMINDER_SENDING_LEASE_MS` recovers interrupted sends. `DISCHARGE_REMINDER_OUTCOMES_LIMIT` controls the maximum recent outcomes shown to department staff (default 50). These settings are also available in `.env.example` and `docker-compose.yml`.

Reminders go to verified, enabled department administrators and medical staff, plus the doctor assigned to the admission. Messages contain the expected date and direct recipients to sign in to review the plan; they omit patient details. The notification panel and `GET /api/v1/reports/discharge-reminders` show outcomes, scoped to the active department and to assigned admissions for doctors. `ACCEPTED` means Resend accepted the request, not that it reached an inbox. An admission with no verified team email is recorded as `NO_RECIPIENT` and will be retried when an eligible address becomes available.

All registrations begin as disabled PATIENT accounts linked to newly created patient records. Email verification activates the patient account. Doctor requests remain PATIENT until an administrator creates/selects an active doctor profile and updates the verified account's role and doctor link in Team access. Registering never claims a pre-existing patient identity by matching a name or birth date.

## Pre-presentation check

Open the frontend and its proxied health endpoint ahead of time, reset the dedicated demo if desired, then verify one transfer and its audit event. Keep the presentation route open to show automatic refresh. Its QR code uses the current origin; a localhost QR requires a phone-reachable deployment instead.

The wake screen retries service health and offers a manual retry after a prolonged cold start. It is not a substitute for an available hosting plan.

The public URL must be taken from the deployed service; it is not invented in this repository. The Render connector returned unauthorized in this session, and the project owner requested commit/push only.
