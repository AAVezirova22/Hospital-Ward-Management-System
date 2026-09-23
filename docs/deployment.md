# Seeded demonstration deployment

The repository includes a Render Blueprint in `render.yaml`. It declares managed PostgreSQL and separate API/frontend services, with `DEMO_SEED=true`, `DEMO_MODE=true`, and `DEMO_PUBLIC_LOGIN=true` on the dedicated demo API. The specified persistent database and always-on services are paid plans; importing the Blueprint is a separate deployment decision. No Render resources were created or changed during this implementation.
The repository includes a Render Blueprint in `render.yaml`. It declares managed PostgreSQL and separate API/frontend services, with `DEMO_SEED=true` and `DEMO_MODE=true`. The database uses paid plan `0.1c-256mb`; both web services use paid plan `0.5c-512mb`. Review [current Render pricing](https://render.com/pricing) before creating or syncing the Blueprint; syncing these plans to existing free resources changes them to paid tiers and starts paid usage. No Render resources were created or changed for this repository update.

The paid database avoids Free Postgres's 30-day expiration and keeps its data across web-service deploys and restarts while the database resource exists. Deleting or replacing the database can remove data. Render's point-in-time recovery window is 3 days on Hobby and 7 days on Pro or higher; logical backup exports are retained for 7 days. Download backups elsewhere for longer retention and back up before changing database configuration ([Render Postgres backups](https://render.com/docs/postgresql-backups), [Free plan limits](https://render.com/docs/free)).

The paid web services do not spin down after 15 minutes of inactivity, unlike Free web services. Deploys and platform maintenance can still restart them, so this avoids free-tier idle cold starts without promising uninterrupted availability. The Blueprint does not attach persistent disks to the web services, so their filesystems are ephemeral; durable operational records belong in Postgres. Assistant upload content and raw conversations are not persisted.

## Configure a new deployment

1. Import this Git repository as a Render Blueprint. Review the paid tiers and current pricing before applying it.
2. Supply `RESEND_API_KEY` privately on the API service. Never put it in Git, a Docker build argument, or a frontend environment variable.
3. Set `PUBLIC_APP_URL` on both services to the actual public HTTPS frontend origin, without a trailing path. This controls email links and social metadata. Rebuild the frontend after changing its public origin or API destination.
4. The Blueprint wires PostgreSQL host, port, database, user and password from the database resource. `DATABASE_URL`, when present, takes precedence and must be a JDBC PostgreSQL URL. Remove old H2 `/tmp` overrides before switching an existing service.
5. Keep `COOKIE_SECURE=true` behind HTTPS. The frontend proxies same-origin `/api` requests; no browser CORS exception is needed.
6. Confirm `/api/v1/health` returns `UP`, then enter the administrator demo. Verify patient count, active admissions, room capacity, procedure reports and planner simulation before presenting.

## Multiple backend instances

The supported multi-instance setup requires all API instances to use the same PostgreSQL database and cookie-based session affinity. Spring Security stores authenticated sessions in each servlet container's memory; configure the load balancer to route requests with the same `JSESSIONID` cookie to the same API instance. Preserve and forward that cookie through the frontend `/api` proxy, including login, CSRF, assistant upload, message, source-removal and clear requests. The servlet session expires after 30 minutes of inactivity, so affinity must last at least as long as the active session.

Assistant conversation turns, uploaded source text, the one-in-flight request guard and login-failure backoff are also held in process memory. Routing a browser session to another instance or restarting its instance loses its login and transient assistant context. The user can sign in again, but the earlier assistant context and uploads cannot be resumed. The AI session record, pending actions and rate-limit windows are stored in PostgreSQL and shared across instances. The one-in-flight guard is per instance, so separate browser sessions for the same account can make simultaneous requests on different instances. Run one API instance if the deployment cannot preserve cookie affinity or needs a global in-flight limit.

## Public demo entry

`DEMO_PUBLIC_LOGIN` defaults to `false`. The Render Blueprint enables it only for the API connected to the dedicated synthetic demo database. This explicitly allows the browser's demo role buttons to sign in through the frontend's private `/api` proxy, whose backend connection does not appear to come from loopback. The server does not trust `X-Forwarded-For` for this decision. Demo mode and seeded demo identities are still required.

If `DEMO_TOKEN` is configured, the token is required even when public login is enabled; keep it on the backend and never expose it to the browser. For other deployments, leave `DEMO_PUBLIC_LOGIN=false` or configure a private token. Do not enable public demo login on a database containing real patient data or user accounts.

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
