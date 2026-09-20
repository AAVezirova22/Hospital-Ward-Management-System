# Seeded demonstration deployment

The repository includes a Render Blueprint in `render.yaml`. It declares managed PostgreSQL and separate API/frontend services, with `DEMO_SEED=true` and `DEMO_MODE=true`. The specified persistent database and always-on services are paid plans; importing the Blueprint is a separate deployment decision. No Render resources were created or changed during this implementation.

## Configure a new deployment

1. Import this Git repository as a Render Blueprint and review the service plans before applying it.
2. Supply `RESEND_API_KEY` privately on the API service. Never put it in Git, a Docker build argument, or a frontend environment variable.
3. Set `PUBLIC_APP_URL` on both services to the actual public HTTPS frontend origin, without a trailing path. This controls email links and social metadata. Rebuild the frontend after changing its public origin or API destination.
4. The Blueprint wires PostgreSQL host, port, database, user and password from the database resource. `DATABASE_URL`, when present, takes precedence and must be a JDBC PostgreSQL URL. Remove old H2 `/tmp` overrides before switching an existing service.
5. Keep `COOKIE_SECURE=true` behind HTTPS. The frontend proxies same-origin `/api` requests; no browser CORS exception is needed.
6. Give the web service the API's private address as `API_INTERNAL_HOSTPORT` (`host:port`) or `API_INTERNAL_URL` (`http://host:port`), then **build** the web service. See the next section; a missing address now fails the build with an explicit message instead of producing a 502 at runtime.
7. Confirm `/api/v1/health` returns `UP`, then enter the administrator demo. Verify patient count, active admissions, room capacity, procedure reports and planner simulation before presenting.

## The `/api` proxy, and what a 502 on `/api/v1/health` means

The browser only ever talks to the frontend origin. Next rewrites `/api/:path*` to the API service, so a 502 from `https://<frontend>/api/v1/health` is the proxy failing to reach Java — it is not the health endpoint, which either answers `{"status":"UP",...}` or a 503 when PostgreSQL is unavailable.

Two facts decide whether the proxy works:

- **The address must be configured.** `API_INTERNAL_URL` (`http://host:port`) wins; otherwise `API_INTERNAL_HOSTPORT` (`host:port`) is used, because the code adds the scheme itself. Take the value from the API service's **Connect → Internal address** in Render. Both services must sit in the same region for the private network to route.
- **The address is read while the web service is built, not when it starts.** Next writes the proxy destination into `.next/routes-manifest.json` during `next build`; `next start` does not re-read it. Setting the variable and restarting the service changes nothing. After adding or changing the address, trigger **Clear build cache & deploy** on the web service.

Outside development a missing address now stops the build with the message *"The hospital API address is not configured…"*, so the misconfiguration appears in the deploy log instead of as an unexplained 502 on every API call. Local `npm run dev` still defaults to `http://127.0.0.1:8080`.

Check the wiring in this order:

1. Open the API service directly and confirm it is live and its own `/api/v1/health` answers.
2. On the web service's **Environment** tab, confirm `API_INTERNAL_HOSTPORT` or `API_INTERNAL_URL` exists and matches the API's current internal address.
3. Rebuild the web service, then re-request `/api/v1/health` through the frontend origin.

If the Blueprint in `render.yaml` and the live services carry different names, the `fromService` wiring in the Blueprint is not what feeds the running web service; fix the names on one side so the wiring is declared in Git rather than kept by hand.

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
