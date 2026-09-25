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
| GET / POST / PUT | `/rooms` / `/rooms/{id}` | RoomInput; GET supports `minFree` and repeated `requiredCapabilities` tags |
| GET / POST / PUT | `/rooms` / `/rooms/{id}` | RoomInput; GET supports `minFree` |
| POST | `/rooms/{id}/holds` | `{bedCount, reason, startsAt, endsAt}`; admin/staff; timed maintenance reservation |
| DELETE | `/rooms/{roomId}/holds/{holdId}` | Admin/staff; cancels a reservation and returns 204 |
| GET / POST / PUT | `/procedures` / `/procedures/{id}` | ProcedureInput; catalogue |
| GET | `/admissions` / `/admissions/{id}` | Scoped stays with patient/doctor/room/procedure details |
| POST | `/admissions` | `{patientId, doctorId, roomId, requiredRoomCapabilities?}` |
| POST | `/admissions/{id}/transfer` | `{roomId, reason, version}` |
| POST | `/admissions/{id}/discharge` | `{version}` |
| POST | `/admissions/{id}/doctor` | `{doctorId, version}` |
| POST | `/admissions/{id}/procedures` | `{medicalProcedureId, doctorId, performedAt, note}` |
| GET / POST / PUT | `/users` / `/users/{id}` | UserInput; admin-only |
| GET | `/audit` | Latest 100 events; admin-only |
| GET | `/workspaces` | Hospitals and departments the account can open, plus the active department. Live join codes are omitted; owners receive `hasJoinCode`. |
| GET | `/workspaces/hospitals/{id}/members?page=0&size=20` | Paginated hospital roster; hospital owner only. Shows OWNER/MEMBER, enabled status, joinedAt, and department role/doctor links for each member. |
| GET | `/workspaces/departments/{id}/members?page=0&size=20` | Paginated department roster; department admin or hospital owner only. Shows role, linked doctor, enabled status, and joinedAt. |
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

Roster endpoints return `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`, and `nextPage`; page size is clamped to 1 to 100, requested pages are clamped to the available range, and results sort by joinedAt descending (unknown dates last) then user ID descending. Membership `joinedAt` is nullable: existing rows have unknown join dates and remain `null`; new membership rows use their insertion time. This timestamp tracks membership creation, not the user account creation date.

DTO definitions and exact field constraints are in `api/Inputs.java`. All edits carry the returned `version`; newly created records start at version zero. Deactivation uses `active:false` or `enabled:false` on the existing record, with version validation. Usernames cannot change. Doctor-role users must link an active doctor. Patients retain their permanent historical identity.

Room `capabilities` and admission `requiredRoomCapabilities` are arrays of up to 30 tags; each tag is trimmed, lowercased and limited to 64 characters. A room must support every requirement when an admission or transfer is saved. Requirements remain attached to an admission for future transfers. Room search filters on every requested tag; the assistant also returns excluded rooms with missing-tag or availability reasons. Removing a capability required by an active occupant returns `409 ROOM_CAPABILITY_IN_USE`.
Room responses include `occupiedBeds`, `heldBeds`, `activeHeldBeds`, `availableBeds`, and current/upcoming `holds`. A hold uses a half-open `[startsAt, endsAt)` window. Upcoming holds reserve placement capacity immediately and release it automatically at `endsAt`; overlapping holds are checked against current occupancy and room capacity. `minFree`, admission, transfer, the planner, and reports all use the same reserved capacity.

## Reports

| Path | Parameters | Result |
| --- | --- | --- |
| `/reports/dashboard` | None | Scoped active admissions, occupied/held/available department capacity, doctors, today's procedures |
| `/reports/dashboard` | None | Scoped active admissions, department capacity, doctors, today's procedures |
| `/reports/operations` | None | Operations metrics and an `overdueDischarges` worklist for active admissions scheduled before today; doctors see only their assigned admissions |
| `/reports/census` | Optional `roomId`, `doctorId` | Currently hospitalized patients, scoped by role |
| `/reports/capacity` | None | Room occupancy and availability |
| `/reports/discharge-reminders` | None | Recent delivery outcomes for the active department; doctors only see admissions assigned to them |
| `/reports/procedures` | Required ISO dates `from`, `to`; optional `patientId`, `doctorId` | Rows, exact decimal total, totals grouped by performing doctor |
| `/reports/procedures.csv` | Same as procedure report | Download containing numeric record, admission and procedure IDs, timestamp and historical cost. Each download writes a `DATA_EXPORTED` audit event with the export type, date range, patient/doctor filters, department, actor, time and row count; the exported rows are not stored |

Report dates are inclusive and interpreted in UTC. Future or inverted invalid date input is validated where applicable; `from > to` is rejected. CSV values are numeric identifiers, ISO timestamps and decimals; user-entered text is omitted to avoid spreadsheet-formula injection.

Discharge reminder outcomes include the expected date, reminder window, status, recipient count, attempt count, provider message ID and a safe error code. They omit recipient addresses and patient or admission identifiers. `ACCEPTED` means the email provider accepted the request; it does not confirm inbox delivery.

## Clinician task reminders and browser push

These routes require an authenticated department staff member (admin, medical staff, or doctor) and an active department. Browser push is available only when `PUSH_VAPID_PUBLIC_KEY` and `PUSH_VAPID_PRIVATE_KEY` are configured and `TASK_REMINDERS_ENABLED=true`; the scheduler defaults to disabled. The browser must also support service workers, Push API, and notifications, and the site must be served over HTTPS (localhost is allowed by browsers for development).

| Method | Path | Request / result |
| --- | --- | --- |
| GET | `/task-reminders/preferences` | Preference fields plus `pushAvailable`, `activeSubscriptions`, and `availableLeadMinutes` |
| PUT | `/task-reminders/preferences` | `{optedIn,timeZone,minutesBefore,quietHoursStart,quietHoursEnd,operationalAlerts}`; choose `minutesBefore` from the response's `availableLeadMinutes`; quiet hours use local `HH:mm` values or both null |
| GET | `/task-reminders/vapid-public-key` | `{publicKey}`; null when server push is unavailable |
| GET | `/task-reminders/subscriptions` | `{subscribed,activeCount,pushAvailable}` |
| POST | `/task-reminders/subscriptions` | Standard browser subscription `{endpoint,p256dh,auth}` |
| DELETE | `/task-reminders/subscriptions/{id}` | Revoke one subscription owned by the current account |
| DELETE | `/task-reminders/subscriptions` | Revoke all subscriptions owned by the current account |
| GET | `/task-reminders/outcomes?limit=50` | Recent status, attempt time/count, sent time, and safe error code; task or patient details are omitted |
| GET | `/task-reminders/operational-outcomes?limit=50` | Operational push delivery status, retry count, and safe error code |
| GET | `/task-reminders/open/{token}` | Resolve opaque notification token to `{taskId,departmentId,dueAt,status}` after authentication and current assignment checks |
| GET | `/task-reminders/open-notification/{token}` | Resolve an operational push token to its in-app notification after authentication and visibility checks |
| POST | `/task-reminders/snooze/{token}` | Optional `{minutes}` (5–240; defaults to 15) |

Push titles and bodies are generic for both task reminders and operational notices; links contain only a random UUID token. Task links use `/app/tasks?reminder=…`, and operational links use `/app/dashboard?notification=…`. They include no patient, task, or notice details. The client must call the corresponding authenticated open route after navigation before requesting task or notification details. Push endpoints are limited to known browser push provider hosts. Revoked provider subscriptions are marked revoked and excluded from future deliveries; retries use a fixed delay and stop after five attempts. Quiet hours are evaluated in the clinician's configured IANA time zone.

## Assistant

| Method | Path | Body / result |
| --- | --- | --- |
| POST | `/assistant/messages` | `{message, sessionId?, route?, selectedPatientId?}`; typed response |
| GET | `/assistant/sessions/{key}` | Owner-only recent metadata, no raw conversations |
| POST | `/assistant/sessions/{key}/clear` | Clear selected-patient context |
| GET | `/ai-actions/{id}` | Owner-only proposal |
| POST | `/ai-actions/{id}/confirm` | Uses the saved proposal; optionally accepts field decisions for uncertain workflow evidence |
| POST | `/ai-actions/{id}/cancel` | Owner-only cancellation |

Response types: `TEXT`, `PATIENT_LIST`, `PATIENT_SUMMARY`, `ROOM_LIST`, `REPORT_RESULT`, `NAVIGATION_COMMAND`, `CONFIRMATION_CARD`, `WORKFLOW_PROPOSAL`, `ERROR`. They include `message`, `data`, `sessionId`, `model`. Workflow steps may return evidence per field; exact excerpts are checked against owned uploaded sources and unverifiable citations are marked as such. `verified` means the exact excerpt was found in an owned source; `location` is the computed character range and `reportedLocation` is the model's row/page description. Fields marked `requiresDecision` must be explicitly accepted or edited in the confirmation body, for example `{ "fieldDecisions": [{"stepKey":"p1","field":"firstName","decision":"ACCEPTED"}] }` or `{ "stepKey":"p1","field":"firstName","decision":"EDITED","value":"Jane" }`. Edited values undergo the same schema, role, ownership and current-state checks as the proposed workflow. Evidence metadata never authorizes a write.

Read tools: `searchPatients`, `getPatientSummary`, `getAvailableRooms`, `getRoomOccupancy`, `getDoctorPatients`, `getAdmission`, `getAdmissions`, `getProcedureStatistics`, `getDashboardSummary`, `listWorkspaces`. Room search accepts comma-separated capability tags and reports excluded rooms with reasons. Admission and transfer proposals apply the saved or requested tags and recheck them on confirmation. Additional tools: `navigate`, `help`, `prepareAdmission`, `prepareTransfer`, `prepareDischarge`. Patient search results include the current hospital/department label. `listWorkspaces` is the only hospital-wide read; it omits join codes.

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
| 409 | `ROOM_CAPABILITY_MISMATCH` | Destination is missing one or more required room tags |
| 409 | `ROOM_CAPABILITY_IN_USE` | An active occupant requires a capability being removed |
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
