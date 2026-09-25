# Verification record

Date: 2026-09-19. All bundled sample data is synthetic.

## Care pathway implementation checks (2026-09-25)

| Check | Result |
| --- | --- |
| Complete Java 21 backend suite with PostgreSQL 17 Testcontainers | **206 passed, 0 failures, 0 errors** |
| Frontend unit tests and TypeScript type-check | **51 passed**; type-check passed |
| Optimized Next.js production build | Passed |
| Desktop (1440px) and phone (390px) pathway browser checks | Passed with a mocked API: document submission remains explicit, edited pending tasks require a refreshed preview before approval, and no horizontal overflow was observed |

Backend integration tests include real PDF, XLSX, and DOCX parser fixtures, consent version and withdrawal checks, per-subscription reminder behavior, and the synthetic discharge pathway from document submission through review, task creation, portal summary, and cancellation. The browser checks used a mocked API, and no live external AI or push provider was called.

## Conversion checks executed

| Check | Result |
| --- | --- |
| Java 21 compilation and executable/deployable WAR packaging | Passed |
| WAR manifest (`WarLauncher` and application start class) | Passed |
| Next.js TypeScript type-check | Passed |
| Next.js optimized production build | Passed |
| Frontend Zod/route contract tests | **6 passed** |
| Frontend formatting check | Passed |
| Standalone Next.js server and `/app/dashboard` response | Passed |
| Same-origin security headers | Passed |
| Runtime npm dependency audit (`--omit=dev`) | **0 reported vulnerabilities** |

The production frontend build uses Next.js App Router and emits a standalone Node server. The backend emits `hospital-1.0.0.war`; it is executable with embedded Tomcat and deployable as a WAR to a compatible external Tomcat 10.1 server.

## Included broader test suite

The repository retains the supplied backend integration/security/concurrency tests and Playwright browser workflows. They cover:

- Authentication, logout, CSRF, password hashing and role authorization.
- Patient validation, admissions, doctor assignments, room transfers, discharge and capacity release.
- Duplicate admission, full-room, stale-version and concurrent final-bed conflicts.
- Procedure recording, immutable historical prices, reports and CSV output.
- Assistant tool selection, safe navigation, rate limits and external-provider protocol handling.
- Proposed AI admission/transfer/discharge actions, ownership, expiry, cancellation and replay protection.
- Audit metadata and end-to-end browser journeys for administration, reports, responsive navigation and reduced motion.

Run the complete backend suite with a disposable PostgreSQL instance or Docker/Testcontainers:

```bash
mvn -f backend/pom.xml verify
```

Run the browser suite after the demo stack is ready:

```bash
cd frontend
npm ci
npx playwright install --with-deps chromium
BASE_URL=http://localhost:8088 E2E_PASSWORD=your-bootstrap-password npm run e2e
```

## Verification limits

This conversion environment did not have a Docker daemon or native PostgreSQL service, so the database integration and browser suites were not re-executed here. The Java sources and test sources compiled successfully during WAR packaging. GitHub Actions and the documented local commands run the full suite against PostgreSQL 17.

No live external AI provider was called. The default local command mode requires no provider credentials.
