# Verification record

Date: 2026-09-19. All bundled sample data is synthetic.

## Doctor appointment checks (2026-10-05)

Implemented on `add-appointments`, based on `main`.

| Check | Result |
| --- | --- |
| Complete backend suite with PostgreSQL 17 Testcontainers | **348 passed, 0 failures, 0 errors** |
| Clean executable WAR packaging | Passed; cleaned stale duplicate generated classes that had prevented local repackaging |
| Frontend TypeScript and unit tests | Passed; **63 tests** in 12 files |
| Production Next.js build and final Docker image builds | Passed |
| Live Docker appointment browser check | Passed: booking, persisted schedule, rejected overlap, cancellation, released availability, assistant confirmation; 1440px desktop and 390px phone inspected |
| OpenClaw configured agent using its live staff browser | Passed: ordinary form booked synthetic appointment #5 with Elena Dimitrova for 2028-11-03 16:30 UTC, verified its scheduled row, cancelled it and verified free availability |
| Independent local database/audit check | #5 is cancelled; both `APPOINTMENT_BOOKED` and `APPOINTMENT_CANCELLED` recorded |
| Existing iMessage gateway probe | Gateway reachable; channel enabled, configured, running, connected, works |

The browser suite used the real API and database, with a browser in `America/New_York` to check that appointment fields and receipts still use the department's UTC time zone. Its synthetic reservations were cancelled afterward. OpenClaw used the owner's configured live model in an isolated session, with message delivery disabled. A fresh physical incoming iMessage is a separate transport check; the probe and isolated agent test do not prove receipt of a particular sender message. OpenClaw's persistent booking instructions were saved in its workspace outside this repository with a backup.

Full-suite verification also corrected existing test-order dependencies in reminder/audit tests, aligned the AI provider-failure expectation with the current response code, and fixed case-preserving SQL aliases for patient-visible care summaries.

## Care pathway implementation checks (2026-09-25)

| Check | Result |
| --- | --- |
| Complete Java 21 backend suite with PostgreSQL 17 Testcontainers | **206 passed, 0 failures, 0 errors** |
| Frontend unit tests and TypeScript type-check | **51 passed**; type-check passed |
| Optimized Next.js production build | Passed |
| Desktop (1440px) and phone (390px) pathway browser checks | Passed with a mocked API: document submission remains explicit, edited pending tasks require a refreshed preview before approval, and no horizontal overflow was observed |

Backend integration tests include real PDF, XLSX, and DOCX parser fixtures, consent version and withdrawal checks, per-subscription reminder behavior, and the synthetic discharge pathway from document submission through review, task creation, portal summary, and cancellation. The browser checks used a mocked API, and no live external AI or push provider was called.

## Care pathway follow-up changes (2026-09-28)

Covers the corrected-identity gate, separate summary publication, reminder auditing and delivery reclaim, and audit-trail completeness. The state above this section describes the pre-change baseline and is retained for history.

| Check | Result |
| --- | --- |
| `mvn -f backend/pom.xml -DskipTests test-compile` | Passed (exit 0) |
| Backend unit tests not requiring a database: `TaskReminderPolicyTest`, `AuditServiceReadTest`, `DischargeReminderServiceTest`, `AiModelTest` | **27 passed, 0 failures, 0 errors** |
| Frontend unit tests and TypeScript type-check | **61 passed**; type-check passed |
| Flyway migration versions are unique | Passed; no duplicate versions after adding `V37` |
| `node scripts/check-text-encoding.mjs` | 557 files valid UTF-8, no mojibake |

Not executed locally, and therefore verified only in CI: the DB-backed integration suites, which need Docker or `TEST_DATABASE_URL`. The changed tests in `PatientDocumentDraftIntegrationTest`, `PatientConsentIntegrationTest`, `TaskReminderIntegrationTest` and `SyntheticDischargePathwayIntegrationTest` are all in that group. `AiSafetyTest` also has one failure that predates this work: a Mockito argument-matcher mismatch in `modelFailureIsStructuredAndRateLimitOnlyAffectsAssistant`.

### Second-pass review (2026-09-28)

The change was re-reviewed adversarially against the diff after the first pass, and the review found defects introduced by the first pass. Each was reproduced in the source before being fixed:

| Defect | Consequence if shipped |
| --- | --- |
| Summary publication split on the server with no client to call it | The end-to-end acceptance criterion was broken: a launch-time summary would silently never reach the portal. Fixed in `91a2da9`. |
| Identity gate refused on `MISSING` as well as `CONFLICT` | A referral letter with a name but no patient ID was refused with a message about a conflict that did not exist, with no recovery. Fixed in `d50e30e`. |
| Identifier match with a contradicting date of birth was labelled `NAME_ONLY` | The most dangerous candidate carried the most reassuring caption, and the test added in `8e98b6c` asserted the correct value, so it would have failed in CI. Fixed in `8142da3`. |
| `V37` added `summary_published_at` with no backfill | Every patient summary already visible in the portal would disappear on upgrade, silently. Fixed in `f3cca03`. |
| Patient chart written before the document bind was validated | A conflicted name or date of birth was committed to the record, the view had already switched to "approved", and the only retry re-ran the call that always failed. Fixed in `8142da3`. |
| `retractSummary` rejected `COMPLETED` runs | A wrong published summary would become unwithdrawable once completion is implemented. Latent. Fixed in `de702c3`. |
| Reclaim reset stranded sends to `PENDING` at any attempt count | Bypassed the `MAX_ATTEMPTS` guard, allowing one attempt past the limit. Fixed in `a384a0e`. |
| Task update notice read the pre-PATCH row from the query cache | The honest-outcome reporting could never report a decline, the one case it exists for. Fixed in `d1f58ee`. |

After these fixes: `mvn -f backend/pom.xml -DskipTests test-compile` exits 0, the 27 non-DB unit tests pass, `npx tsc --noEmit` exits 0, and `npm test` reports 12 files and 61 tests passed. The integration suites above remain CI-only.

The two defects that would have been visible to a reviewer without a database — the contradicting-date-of-birth label and the candidate-ordering assumption in the new identity test — are noted here because compiling a test is not running it. They are the reason the CI suite matters for the remaining DB-backed assertions.

This work also repaired the backend build, which did not compile at `daef984`: Dependabot had moved `tika-parsers-standard-package` to 4.0.0, where that artifact is a POM-only aggregator with no jar, and three `AiSourceService` call sites used 4.0.0-removed APIs. The dependency is now declared with `<type>pom</type>`, `tika-parser-pdf-module` is explicit, and OCR is configured through `OcrConfig` with `NO_OCR` preserved so scanned images are still rejected rather than silently OCR'd.

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
