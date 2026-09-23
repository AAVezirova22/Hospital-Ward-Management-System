# REST API

All paths start with `/api/v1`. Except health, login and CSRF-token retrieval, endpoints require a session. POST/PUT operations require the session's CSRF token. JSON request validation rejects unknown fields. Errors return `timestamp`, `status`, `code`, `message`, `path`. Send `X-Department-Id` to open a joined department; the last selected department is also stored on the session.

## Core endpoints

| Method | Path | Request / result |
| --- | --- | --- |
| GET | `/health` | Readiness alias (kept for existing probes) |
| GET | `/health/live` | Liveness: process is serving; no dependency checks |
| GET | `/health/ready` | Readiness: database reachable and migrations applied; `503` when not ready |
| GET | `/auth/csrf` | `{token, headerName}` |
| POST | `/auth/login` | Form-encoded `username`, `password`; returns account without hash |
| GET | `/auth/me` | Current account |
| POST | `/auth/logout` | Invalidates session, returns 204 |
| GET | `/patients?q=...` | Scoped demographic matches |
| GET | `/patients/{id}` | Patient plus scoped admission/room/procedure history |
| POST / PUT | `/patients` / `/patients/{id}` | PatientInput |
| GET / POST / PUT | `/doctors` / `/doctors/{id}` | DoctorInput; writes admin-only |
| GET / POST / PUT | `/rooms` / `/rooms/{id}` | RoomInput; GET supports `minFree` |
| GET / POST / PUT | `/procedures` / `/procedures/{id}` | ProcedureInput; catalogue |
| GET | `/admissions` / `/admissions/{id}` | Scoped stays with patient/doctor/room/procedure details |
| POST | `/admissions` | `{patientId, doctorId, roomId}` |
| POST | `/admissions/{id}/transfer` | `{roomId, reason, version}` |
| POST | `/admissions/{id}/discharge` | `{version}` |
| POST | `/admissions/{id}/doctor` | `{doctorId, version}` |
| POST | `/admissions/{id}/procedures` | `{medicalProcedureId, doctorId, performedAt, note}` |
| GET / POST / PUT | `/users` / `/users/{id}` | UserInput; admin-only |
| GET | `/audit` | Latest 100 events; admin-only |
| GET | `/workspaces` | Hospitals and departments the account can open, plus the active department. Live join codes are omitted; owners receive `hasJoinCode`. |
| POST | `/workspaces/hospitals` | `{name, departmentName}`; owner of the hospital and admin of its first department |
| POST | `/workspaces/hospitals/{id}/departments` | `{name}`; hospital owner only |
| POST | `/workspaces/join` | `{code}`; hospital codes add hospital membership, department codes add medical staff access. Expired or consumed single-use codes are rejected. |
| GET | `/workspaces/hospitals/{id}/code` | Reveal the hospital join code; owner only. Audited as `JOIN_CODE_VIEWED`. |
| GET | `/workspaces/departments/{id}/code` | Reveal the department join code; department administrator only. Audited as `JOIN_CODE_VIEWED`. |
| POST | `/workspaces/hospitals/{id}/code` | Replace the hospital join code; owner only. Optional `{expiresInHours, singleUse}` |
| POST | `/workspaces/departments/{id}/code` | Replace the department join code; department administrator only. Optional `{expiresInHours, singleUse}` |
| POST | `/workspaces/hospitals/{id}/leave` | Leave a hospital; last owner is rejected |
| POST | `/workspaces/departments/{id}/leave` | Leave a department |
| POST | `/workspaces/hospitals/{id}/owners` | `{userId}`; hospital owner only |
| POST | `/workspaces/departments/{id}/roles` | `{userId, role, doctorId?}`; owner or department administrator. `DOCTOR` creates a doctor row in that department when `doctorId` is omitted. |

DTO definitions and exact field constraints are in `api/Inputs.java`. All edits carry the returned `version`; newly created records start at version zero. Deactivation uses `active:false` or `enabled:false` on the existing record, with version validation. Usernames cannot change. Doctor-role users must link an active doctor. Patients retain their permanent historical identity.

## Reports

| Path | Parameters | Result |
| --- | --- | --- |
| `/reports/dashboard` | None | Scoped active admissions, department capacity, doctors, today's procedures |
| `/reports/census` | Optional `roomId`, `doctorId` | Currently hospitalized patients, scoped by role |
| `/reports/capacity` | None | Room occupancy and availability |
| `/reports/procedures` | Required ISO dates `from`, `to`; optional `patientId`, `doctorId` | Rows, exact decimal total, totals grouped by performing doctor |
| `/reports/procedures.csv` | Same as procedure report | Download containing numeric record, admission and procedure IDs, timestamp and historical cost |

Report dates are inclusive and interpreted in UTC. Future or inverted invalid date input is validated where applicable; `from > to` is rejected. CSV values are numeric identifiers, ISO timestamps and decimals; user-entered text is omitted to avoid spreadsheet-formula injection.

## Assistant

| Method | Path | Body / result |
| --- | --- | --- |
| POST | `/assistant/messages` | `{message, sessionId?, route?, selectedPatientId?}`; typed response |
| GET | `/assistant/sessions/{key}` | Owner-only recent metadata, no raw conversations |
| POST | `/assistant/sessions/{key}/clear` | Clear selected-patient context |
| GET | `/ai-actions/{id}` | Owner-only proposal |
| POST | `/ai-actions/{id}/confirm` | No client-supplied mutation payload; uses saved proposal |
| POST | `/ai-actions/{id}/cancel` | Owner-only cancellation |

Response types: `TEXT`, `PATIENT_LIST`, `PATIENT_SUMMARY`, `ROOM_LIST`, `REPORT_RESULT`, `NAVIGATION_COMMAND`, `CONFIRMATION_CARD`, `ERROR`. They include `message`, `data`, `sessionId`, `model`. The client validates the response envelope and renders trusted React components.

Read tools: `searchPatients`, `getPatientSummary`, `getAvailableRooms`, `getRoomOccupancy`, `getDoctorPatients`, `getAdmission`, `getAdmissions`, `getProcedureStatistics`, `getDashboardSummary`, `listWorkspaces`. Additional tools: `navigate`, `help`, `prepareAdmission`, `prepareTransfer`, `prepareDischarge`. Patient search results include the current hospital/department label. `listWorkspaces` is the only hospital-wide read; it omits join codes.

## Representative errors

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Invalid/unknown request fields |
| 400 | `PATIENT_AMBIGUOUS` / `DOCTOR_AMBIGUOUS` | Need a unique target |
| 401 | `UNAUTHENTICATED` / `INVALID_CREDENTIALS` | Sign-in required or rejected |
| 403 | `ACCESS_DENIED` | Role, entity ownership or CSRF restriction |
| 403 | `DEPARTMENT_ACCESS_DENIED` | The account has not joined the requested department |
| 403 | `HOSPITAL_OWNER_REQUIRED` | Only a hospital owner can manage that hospital |
| 403 | `DEPARTMENT_ADMIN_REQUIRED` | Only a department administrator can replace its code |
| 400 | `INVALID_CODE` | Join code is missing, malformed, or unknown |
| 400 | `CODE_EXPIRED` | Join code is past its expiry |
| 404 | `NOT_FOUND` | Requested record absent |
| 409 | `ROOM_CAPACITY_EXCEEDED` | Destination is full or inactive |
| 409 | `STALE_STATE` | Version changed since review |
| 409 | `ALREADY_ADMITTED` / `ADMISSION_CLOSED` | Invalid admission lifecycle transition |
| 409 | `ACTION_EXPIRED` / `ACTION_CONSUMED` | Expired or previously resolved proposal |
| 409 | `DATA_CONFLICT` | Uniqueness, optimistic lock or lock contention conflict |
| 401 | `SESSION_EXPIRED` | The session passed its maximum lifetime; sign in again |
| 429 | `AI_RATE_LIMIT` | Assistant quota or in-flight request limit |
| 429 | `RATE_LIMITED` | Confirmation-email resend limit |
| 503 | `DATABASE_TIMEOUT` / `DATABASE_UNAVAILABLE` | Request cancelled without saving, or database unreachable; honour `Retry-After` |

## Rate-limit headers

Rate-limited endpoints (`POST /assistant/messages`, registration confirmation resends) return the remaining budget on every checked response:

| Header | Meaning |
| --- | --- |
| `RateLimit-Limit` | Requests allowed in the current window |
| `RateLimit-Remaining` | Requests left in the window after this one |
| `RateLimit-Reset` | Seconds until the window resets |
| `Retry-After` | On `429` only: seconds to wait before retrying |

Clients should wait at least `Retry-After` seconds after a `429` and must not retry a rejected request sooner; the rejected request had no effect. An assistant request rejected because another one is still running returns `Retry-After: 1` without budget headers.

## Session lifetime

Sessions end after 30 minutes of inactivity and, regardless of activity, `SESSION_MAX_LIFETIME` after sign-in (default `12h`). The request that crosses the limit returns `401 SESSION_EXPIRED` and the session is invalidated; the client should fetch a new CSRF token and send the user to sign in again. Pending unsaved form input is not preserved by the server.
