# Medcore - hospital and department management

A Next.js App Router + TypeScript frontend, Java 21 / Spring Boot API on Tomcat, and PostgreSQL database implementing the attached hospital-management plan. Staff can create hospitals and departments, then join them with rotating codes. The operations assistant uses the same authorized business services as the standard UI. Critical operations require a review step; AI writes require an owned, expiring confirmation record.

## Run with Docker

Requirements: Docker Engine with Compose.

```bash
cp .env.example .env
# Set DATABASE_PASSWORD and BOOTSTRAP_PASSWORD in .env.
docker compose up --build -d
```

Open **http://localhost:8088**. Sign in as `admin` using `BOOTSTRAP_PASSWORD`.

The bootstrap password must be at least 12 characters and at most 72 UTF-8 bytes. A shorter password prevents the backend from starting, so the frontend cannot create a secure session. Inspect `docker compose logs backend` if sign-in reports that the department service is unavailable. After correcting `.env`, run `docker compose up -d backend`. Existing accounts are not reset by changing the bootstrap password.

The cinematic redesign, verification results, and AI connection status are documented in [the redesign notes](docs/redesign.md).

Demo seeding is enabled by default. An empty database gets `admin`, `staff`, and `doctor` accounts using the same bootstrap password, three doctors, eight rooms, 28 synthetic patients, 14 active and 12 discharged admissions, procedures, transfer history and audit activity. Dates are relative to seeding. Set `DEMO_SEED=false` for an empty workspace with only the administrator. Set `DEMO_MODE=true` only for a dedicated synthetic environment to enable role entry and an explicit destructive reset. Bootstrap runs once inside a transaction; restarts preserve records.

The database volume survives container restarts. `docker compose down` preserves data. Next.js rewrites same-origin `/api` requests to Java over the Compose network. Database and API ports are not exposed publicly; the web interface binds to loopback.

The local Compose configuration uses HTTP with `COOKIE_SECURE=false`. A deployed instance should sit behind HTTPS with `COOKIE_SECURE=true`, new credentials, an organization-approved database backup/restore process, and the organization’s data-access and retention configuration. Follow the [database backup security and recovery runbook](docs/database-backup-security.md). No real patient data is bundled.

## Develop locally

Requirements: JDK 21, Maven 3.9+, Node 22 LTS, PostgreSQL 17.

```bash
# Point these at a local PostgreSQL database you created.
export DATABASE_URL=jdbc:postgresql://localhost:5432/hospital
export DATABASE_USER=hospital
export DATABASE_PASSWORD=your-database-password
export BOOTSTRAP_PASSWORD=your-strong-bootstrap-password
export COOKIE_SECURE=false
export DEMO_SEED=true
mvn -f backend/pom.xml spring-boot:run
```

In a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:3000. The Next.js server rewrites requests to the API at port 8080. Set `API_INTERNAL_URL` when the backend is at another origin.

The backend builds as `backend/target/hospital-1.0.0.war`. It runs directly with its embedded Tomcat (`java -jar ...war`) and can also be deployed as a WAR to a compatible external Tomcat 10.1 installation.

## Verification

```bash
# Starts a disposable PostgreSQL 17 Testcontainer when TEST_DATABASE_URL is absent.
mvn -f backend/pom.xml verify

cd frontend
npm ci
npm run build
npx playwright install --with-deps chromium
# Requires the frontend and backend to be running with demo data.
E2E_PASSWORD=your-bootstrap-password npm run e2e
```

To test against an existing **disposable** PostgreSQL database, supply `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, and `TEST_DATABASE_PASSWORD`. The integration suite creates synthetic data. It does not run against a production database. On first test initialization, the test administrator password is `IntegrationPassword123!`.

For the Compose application, run the browser suite with `BASE_URL=http://localhost:8088 E2E_PASSWORD=... npm run e2e` from `frontend`.

GitHub Actions runs backend tests, TypeScript compilation, a production frontend build, and browser tests against a native PostgreSQL 17 service. See [the verification record](docs/verification.md) for the tests actually executed in this delivery environment and their limits.

## Assistant modes

| Mode | Configuration | Behavior |
| --- | --- | --- |
| `local` | Default | Deterministic offline command interpreter, visibly labeled in the UI. No external model or credentials required. |
| `external` | `AI_URL`, `AI_MODEL`, optional `AI_API_KEY` | Calls a configured OpenAI-compatible chat-completions tool endpoint. Up to eight sequential validated tool calls per request, including file-based workflow planning. |
| `off` | `AI_MODE=off` | Structured assistant-unavailable response; all conventional workflows remain usable. |

`AI_URL` is the complete administrator-configured endpoint, such as an organization's HTTPS `/v1/chat/completions` URL; it is never a model-supplied URL. Each provider call has a 15-second timeout and disallows redirects. The model receives the user's request, role/route/selected ID, allowed schemas, attached extracted text, connected file names, authorized tool results and up to six recent conversational turns. It never gets database credentials or arbitrary API access. Source text and recent turns are held in memory for up to 30 minutes of availability; original uploads and raw conversations are not persisted to the database. External-model deployment uses your chosen provider and credentials. The adapter is tested using a local HTTP fixture; no live provider credentials are included.

For Google AI Studio, use `AI_MODE=external`, `AI_URL=https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`, and `AI_MODEL=gemini-3.5-flash-lite`. Put your key in `AI_API_KEY` in the ignored root `.env` and recreate the backend. The key is passed only to the Java backend. Google HTTP 402 (`RESOURCE_EXHAUSTED`) means the project's prepaid credits need replenishing in AI Studio; HTTP 403 (`PERMISSION_DENIED`) requires resolving project access. Restarting Medcore cannot resolve either provider-side restriction. See [Google's compatibility documentation](https://ai.google.dev/gemini-api/docs/openai).

### Files on your PC and uploads

Open **Operations assistant**, choose **Upload files** or **Connect folder**, then describe what you want built. For example: "Find the setup files in this folder and create a hospital with its rooms, doctors and patients" or "Use this spreadsheet to prepare the admissions, and ask me about missing fields."

Connected folders use the browser's existing [File System Access API](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access). The assistant can inspect the folder you choose and its subfolders; it does not get unrestricted access to the whole PC. Browsers without the directory picker use a folder-selection fallback. Names are shared when you send a message; file contents are uploaded only when requested by the agent. Ordinary uploads work independently.

Document extraction reuses [Apache Tika](https://tika.apache.org/). Supported inputs are TXT, Markdown, CSV, TSV, JSON, PDF, DOCX, XLSX, PPTX, ODT, ODS and RTF. Natural-language planning requires `AI_MODE=external`; local command mode explains this instead of pretending to interpret files.

The assistant can compose supported app operations into one reviewed workflow: create a hospital and initial department, patients, doctors, rooms and procedure catalogue entries; admit, transfer or discharge patients; and record performed procedures. Steps can reference records created by earlier steps. Missing fields should prompt a follow-up. Every proposal shows fields and source names; **Confirm workflow** executes through the existing authorized business services in one transaction. Failed workflows roll back completely. Confirmation ownership, expiry, cancellation and replay checks also apply.

See [file access, limits and verification](docs/assistant-files.md) for details. Try:

- `Find Petrov`
- `Rooms with two free beds`
- `Show department status`
- `Procedures today`
- `Show admissions created this week`
- `Show current patients of Dr. Dimitrova`
- `summary` while a patient dossier is open
- `Move him to room 307` while an admitted patient's dossier is open
- `discharge him` while a patient dossier is open
- `Admit Vera Angelova doctor Dimitrova room 307`
- `open reports`

The local interpreter supports these documented command patterns and falls back to help for unsupported phrasing. Missing placement details open the conventional form. It is not a general-purpose language model. Clinical decisions and security administration are excluded from tool access.

## What is included

- Session login/logout, CSRF protection, password hashing, current-account revalidation and three roles.
- Patient demographics and search; doctor, room, procedure and user administration.
- Admission, attending-doctor assignment, room-transfer history, discharge and bed release.
- Transactional capacity checks, active-admission uniqueness and optimistic version checks.
- Procedure recording with immutable historical prices and operational notes.
- Dashboard, hospitalized-patient census, room capacity, filters by doctor/room, procedure period/patient reports, grouped totals and CSV export.
- Keyboard-accessible assistant, structured result cards, safe navigation, read tools, expiring proposals, ownership checks, cancellation, revalidation and replay protection.
- Audit history without passwords, raw prompts or clinical notes in audit metadata.
- Responsive dark interface, route transitions, post-save feedback, and reduced-motion support.
- Flyway schema, Dockerfiles, Compose, CI, integration tests, provider-contract tests, prompt-evaluation fixtures and Playwright workflows.

## Source map

```text
backend/src/main/java/com/example/hospital/
  api/         REST controllers, DTO validation, standard errors
  domain/      JPA models
  repository/  Spring Data repositories
  service/     Hospital, user, audit and bootstrap services
  security/    Session security and actor policy
  ai/          Model interface, adapters, registry, sessions and confirmation
backend/src/main/resources/db/migration/
frontend/app/  Next.js App Router entry points and metadata
frontend/src/  Client screens, typed API client, styles
frontend/e2e/  Browser workflow tests
docs/          Architecture, API, traceability, demo and verification
```

The supplied specification is preserved verbatim in [docs/plan.md](docs/plan.md). Source is maintained at [Hospital-Ward-Management-System](https://github.com/AAVezirova22/Hospital-Ward-Management-System).

## Demo, accounts and messaging

The workspace includes a staged ward planner, read-only arrival simulations, operational alerts and trends, report chart drill-downs, keyboard command search, assistant placement previews, and a projector presentation route. Patients can register and confirm their email through Resend. Doctor applicants require email verification and administrator approval; patient accounts only see their own care history.

See [deployment and email setup](docs/deployment.md) for the seeded PostgreSQL Render Blueprint and required private environment settings. See [iMessage integration options](docs/imessage-integration.md) for existing gateways and the future account-linking design. iMessage is documented, not connected.
