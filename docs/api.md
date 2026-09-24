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
| GET / POST / PUT | `/rooms` / `/rooms/{id}` | RoomInput; GET returns a page (`items`, `page`, `size`, `totalElements`) and filters by `q`, `active`, `roomId`, `minFree` and repeated `requiredCapabilities` tags. `availableBeds` subtracts occupants and upcoming maintenance holds, matching placement checks |
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
| GET | `/audit` | Admin-only; optional `eventType`, `actorId`, `entityType`, `entityId`, `source`, `from`, `to`, `page` (default 0), and `size` (default 50, max 200) filters |
| GET | `/audit/export.csv` | Admin-only CSV export; reuses audit filters, accepts `limit` from 1 to 1000 (default 1000), and returns 400 if more rows match. `profile=redacted` is for external reviewers: actors become per-export pseudonyms (`A1`, `A2`, …), and the entity ID and metadata columns are removed. The response carries `X-Audit-Export-Profile` and `X-Redacted-Fields`, and the `DATA_EXPORTED` audit event records the profile and removed fields. The default is `profile=full`. |
| GET / POST / PUT | `/users` / `/users/{id}` | UserInput; admin-only. Optional `reason` (max 300 characters) records context for permission changes. |
| GET | `/audit` | Latest 100 events; admin-only |
| GET | `/security/events?status=ACTIVE&includeInfo=false&page=0&size=25` | Department administrators only. Security review queue for the active department, highest severity first, with `counts` (`open`, `investigating`, `critical`, `unacknowledged`). `status` is `ACTIVE` (open and investigating), `ALL`, or one status. `INFO` entries are hidden unless `includeInfo=true`. |
| POST | `/security/events/{id}/acknowledge` | Marks the entry as seen by the calling administrator; audited as `SECURITY_EVENT_ACKNOWLEDGED` |
| PUT | `/security/events/{id}` | `{status, note?}` with status `OPEN`, `INVESTIGATING`, `RESOLVED` or `DISMISSED`; acknowledges if not yet acknowledged; audited as `SECURITY_EVENT_UPDATED` |

The queue groups related signals into one entry per department and pattern:

| Category | Source | Grouping | Severity |
| --- | --- | --- | --- |
| `FAILED_LOGIN` | Failed or backoff-blocked sign-ins for a known staff account, counted in each of its departments | Account and UTC day | `INFO` for 1–2, `WARNING` from 3, `CRITICAL` from 10 |
| `ACCESS_DENIED` | Refused operations (`ACCESS_DENIED` audit events) | Account and UTC day | Escalates like `FAILED_LOGIN` |
| `JOIN_CODE` | Rejected codes (escalates per account and day), plus code rotations and reveals (`INFO`, per workspace and day) | See source | See source |
| `ROLE_CHANGE` | Role grants, account saves and member removals (`WARNING`), hospital ownership grants (`CRITICAL`) | Acting account and hour | Highest in the group |

While an entry is `OPEN` or `INVESTIGATING`, new matching signals increase `occurrences` and update `lastSeenAt`. After `RESOLVED` or `DISMISSED`, the next signal opens a new entry. Entries record the account and action, never passwords, source addresses or clinical data. Unknown usernames are left to login backoff and do not create entries.

Opening `GET /patients/{id}`, `GET /admissions/{id}` or the assistant's `getPatientSummary` tool records a `PATIENT_VIEWED` or `ADMISSION_VIEWED` audit event with actor, department, record ID, source (`UI` or `AI`) and time. No field values are stored. Lists, searches and failed lookups are not recorded, and a person's own portal view is not recorded. Repeat views of the same record by the same user within `AUDIT_READ_DEDUPE_WINDOW` are recorded once.
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
| POST | `/workspaces/hospitals/{id}/leave?reason=...` | Leave a hospital; last owner is rejected. Optional reason is limited to 300 characters. |
| POST | `/workspaces/departments/{id}/leave?reason=...` | Leave a department; optional reason is limited to 300 characters. |
| DELETE | `/workspaces/hospitals/{id}/members/{userId}?reason=...` | Revoke membership; hospital owner only. Optional reason is limited to 300 characters. |
| DELETE | `/workspaces/departments/{id}/members/{userId}?reason=...` | Revoke membership; owner or department administrator. Optional reason is limited to 300 characters. |
| POST | `/workspaces/hospitals/{id}/owners` | `{userId, reason?}`; hospital owner only. Returns `202` with a pending co-owner invitation: ownership changes only when the member accepts within 7 days. Reason is limited to 300 characters. |
| POST | `/workspaces/hospitals/{id}/ownership-transfers` | `{userId, stepDown, confirmation?, reason?}`; hospital owner only. `stepDown=true` hands over your ownership on acceptance and requires `confirmation` to equal the hospital name. Returns `202` with the pending transfer. One open request per member (`409 TRANSFER_PENDING`). Audited as `OWNERSHIP_TRANSFER_REQUESTED`. |
| GET | `/workspaces/ownership-transfers` | Open requests: `incoming` (addressed to you) and `outgoing` (for hospitals you own) |
| POST | `/workspaces/ownership-transfers/{id}/accept` | Addressed member only. Grants ownership (`HOSPITAL_OWNER_GRANTED` with `transferId` and `requestedBy`) and, for a handover, removes the requester's ownership (`HOSPITAL_OWNER_STEPPED_DOWN`). Fails with `409 TRANSFER_INVALID` if the requester no longer owns the hospital |
| POST | `/workspaces/ownership-transfers/{id}/decline` / `cancel` | Decline (addressed member) or cancel (any owner); audited. Closed, expired or foreign requests return `404 TRANSFER_NOT_FOUND` |
| POST | `/workspaces/departments/{id}/roles` | `{userId, role, doctorId?, reason?}`; owner or department administrator. `DOCTOR` creates a doctor row in that department when `doctorId` is omitted; reason is limited to 300 characters. |

Permission-change audit metadata includes the target user and workspace IDs with before/after role or owner state. Department role changes also include before/after doctor links; account edits distinguish account role and doctor link from department membership role and doctor link. Department-scoped events are stored under the affected department even when a hospital owner acts from another active department. Hospital-level events retain the active department scope and identify the hospital in metadata. A department-code join records whether it also created hospital membership; hospital leave/revocation writes a department-scoped event for each removed department membership. Optional reasons containing obvious credential or token material (including password/token assignments, bearer credentials, or JWT-shaped values) are rejected and never stored; passwords, join codes, and session tokens are not audit fields.
| POST | `/workspaces/hospitals/{id}/leave` | Leave a hospital; last owner is rejected |
| POST | `/workspaces/departments/{id}/leave` | Leave a department |
| POST | `/workspaces/departments/{id}/roles` | `{userId, role, doctorId?}`; owner or department administrator. `DOCTOR` creates a doctor row in that department when `doctorId` is omitted. |
| PUT | `/workspaces/departments/{id}/members/{userId}/expiry` | `{expiresAt}` (ISO-8601 instant, or `null` to remove the limit); owner or department administrator. The time must be in the future and cannot be set on your own membership (`409 SELF_EXPIRY`). From `expiresAt` the member gets `403 MEMBERSHIP_EXPIRED` for that department, it disappears from their `/workspaces` list, and discharge reminders stop. The membership row stays until it is extended or removed. `MEMBERSHIP_EXPIRY_NOTICE` (default `3d`) before the end, the member gets one personal in-app notice. Changing the time sends a new notice. Audited as `MEMBERSHIP_EXPIRY_SET`. `/workspaces` departments carry `accessExpiresAt`, and `/users` accounts carry `membershipExpiresAt`. |
| GET | `/workspaces/hospitals/{id}/access-review?inactiveAfterDays=90` | Hospital owner only. Every workforce member of the hospital (patient accounts excluded) with account state, `lastLoginAt`, `inactive` (no sign-in within the period), owner flag, department roles, linked doctor name and `latestReview`, plus counts per outcome. Contains no clinical records, credentials or contact details. |
| POST | `/workspaces/hospitals/{id}/access-review/{userId}` | `{outcome, note?}` with outcome `KEEP`, `CHANGE` or `REVOKE`; hospital owner only. Stores the decision with reviewer and time, keeps earlier decisions, and audits `ACCESS_REVIEWED`. Recording `CHANGE` or `REVOKE` does not change access; use the role and member endpoints to act on it. |

Roster endpoints return `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`, and `nextPage`; page size is clamped to 1 to 100, requested pages are clamped to the available range, and results sort by joinedAt descending (unknown dates last) then user ID descending. Membership `joinedAt` is nullable: existing rows have unknown join dates and remain `null`; new membership rows use their insertion time. This timestamp tracks membership creation, not the user account creation date.

DTO definitions and exact field constraints are in `api/Inputs.java`. All edits carry the returned `version`; newly created records start at version zero. Deactivation uses `active:false` or `enabled:false` on the existing record, with version validation. Usernames cannot change. Doctor-role users must link an active doctor. Patients retain their permanent historical identity.

Audit history uses the active department automatically. Text filters are trimmed and case-insensitive; `from` and `to` are inclusive department-local calendar dates. Paging is timestamp-descending with audit ID as a stable tie-breaker. The CSV export has a maximum of 1000 matching events per request; narrow the filters when it returns `EXPORT_LIMIT_EXCEEDED`. Its columns are `Department ID`, `Audit ID`, `Actor ID`, `Event type`, `Entity type`, `Entity ID`, `Source`, `Timestamp`, and `Metadata`. Export actions are audited after the export rows are selected, and the exported row contents are not copied into that audit entry.

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

Read tools: `searchPatients`, `getPatientSummary`, `getAvailableRooms`, `getRoomOccupancy`, `getDoctorPatients`, `getAdmission`, `getAdmissions`, `getProcedureStatistics`, `getDashboardSummary`, `listWorkspaces`. Room search accepts comma-separated capability tags and reports excluded rooms with reasons. Admission and transfer proposals apply the saved or requested tags and recheck them on confirmation. Additional tools: `navigate`, `help`, `prepareAdmission`, `prepareTransfer`, `prepareDischarge`. Patient search results include the current hospital/department label. `listWorkspaces` is the only hospital-wide read; it omits join codes.

## Representative errors

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Invalid/unknown request fields |
| 400 | `PATIENT_AMBIGUOUS` / `DOCTOR_AMBIGUOUS` | Need a unique target |
| 401 | `UNAUTHENTICATED` / `INVALID_CREDENTIALS` | Sign-in required or rejected |
| 403 | `ACCESS_DENIED` | Role, entity ownership or CSRF restriction |
| 403 | `DEPARTMENT_ACCESS_DENIED` | The account has not joined the requested department |
| 403 | `MEMBERSHIP_EXPIRED` | The account's time-limited membership of the requested department has ended |
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
| 429 | `RATE_LIMITED` | Request budget spent: sign-in or registration per client address, searches/reports/exports per account, or the confirmation-email resend limit |
| 503 | `DATABASE_TIMEOUT` / `DATABASE_UNAVAILABLE` | Request cancelled without saving, or database unreachable; honour `Retry-After` |

## Idempotency keys

These `POST` endpoints accept an optional `Idempotency-Key` header (1–128 characters from letters, digits and `. _ : -`), so a client that timed out can retry without repeating the change:

- `/patients`
- `/admissions`, `/admissions/{id}/transfer`, `/discharge`, `/doctor`, `/procedures`
- `/ai-actions/{id}/confirm`
- `/rooms/{id}/holds`

| Situation | Response |
| --- | --- |
| First request with a key | Runs normally; the response is stored |
| Retry with the same key, path, department and body | The stored status and body, with `Idempotent-Replayed: true`; nothing runs again |
| Same key with a different path, department or body | `422 IDEMPOTENCY_KEY_REUSED` |
| Retry while the first request is still running | `409 IDEMPOTENCY_IN_PROGRESS`; retry shortly |
| Malformed key | `400 INVALID_IDEMPOTENCY_KEY` |

Keys belong to the signed-in account; another account using the same key starts its own request. Client errors (`4xx`) are stored and replayed. Server errors (`5xx`) and responses over 64 KB are not stored, so those requests can be retried. Stored responses, which may contain the same patient data as the original response, expire after `IDEMPOTENCY_KEY_TTL` (default `24h`). Generate a fresh random key (for example a UUID) for each intended change. Requests without the header behave as before.
## Retrying after a provider failure

When the assistant provider times out or returns an unusable answer, `POST /assistant/messages` responds with `responseType: "ERROR"` and `data`:

| Field | Meaning |
| --- | --- |
| `retryable` | Whether this request may be retried |
| `retryToken` | One-time token for the retry (only when `retryable`) |
| `retryAfterSeconds` | Suggested wait: 2 s, then 4 s |
| `attemptsLeft` | Retries remaining (at most 2 per original request) |

To retry, send the same `message` again with the returned `sessionId` and `retryToken`. The token works once, only for the same account and identical message text, and only within 10 minutes. Otherwise the answer is `409 RETRY_NOT_ALLOWED`, and the request must be sent as a new message. A failed request never applied a change, and a retry asks the model again from scratch: it cannot replay a completed mutation or revive an expired proposal, and any new proposal still needs its own confirmation. Retries count towards the normal assistant rate limit. Only a SHA-256 hash of the message is stored, to match the retry.
## Assistant usage and cost

`GET /reports/ai-usage?from=YYYY-MM-DD&to=YYYY-MM-DD` (administrators only; the default is the last 30 days, at most 366 days, using department-local dates) returns assistant request counts and provider-reported tokens for the active department. Figures are given as `totals` and per model (`byModel`), with `estimatedCost` in `currency`.

Tokens come from the `usage` block of OpenAI-compatible responses and are stored per interaction as `prompt_tokens` and `completion_tokens`. Prompts and replies are never stored. `requests_with_usage` shows how many requests reported tokens; the local model and providers without usage data count as requests only. The cost estimate uses `AI_COST_INPUT_PER_MILLION` and `AI_COST_OUTPUT_PER_MILLION` (prices per million tokens, in `AI_COST_CURRENCY`, default `USD`). When no price is set, `estimatedCost` is `null` and `pricingConfigured` is `false`. The estimate ignores provider-side discounts, caching and minimum charges, so compare it with the provider invoice.
## Pagination

Large collections (`/patients`, `/admissions`, `/doctors`, `/rooms`, `/procedures`, workspace rosters) all return the same page body: `items`, `page` (zero-based), `size`, `totalElements`, `totalPages`, `hasNext`, `nextPage`. `/audit` keeps its established `page`/`size`/`total`/`events` body. Every one of these responses also carries:

| Header | Meaning |
| --- | --- |
| `X-Total-Count` | Total matching records across all pages |
| `Link` | RFC 8288 relative links with `rel="first"`, `"prev"` (when not on the first page), `"next"` (when more pages exist) and `"last"` |

Links keep the request's other filters and set `page` and `size` explicitly. An empty result points `first` and `last` at page 0. Page sizes remain bounded per endpoint (for example at most 100 for catalogues and 200 for audit).
## Workflow dry run

`POST /assistant/workflows/dry-run` with `{"plan": ...}` takes the workflow proposal JSON, either as a string (the assistant's `prepareWorkflow` format) or as an object. It checks every step without saving anything and without creating a pending proposal. The plan runs through the same operations as a confirmed workflow, inside a transaction that is always rolled back, so field validation, references, capacity, capability, uniqueness and role checks match confirmation. The response contains `valid`, `committed: false`, the count of `operations` by type, and `capacityChanges` for existing rooms (`occupiedBefore`/`occupiedAfter`). An invalid plan returns `valid: false` with the `code` and `message` confirmation would have produced. Nothing is audited or notified because nothing commits. Database ID sequences may still advance. A dry run is not a reservation: state can change before a real proposal is confirmed, and confirmation re-checks everything.
## Expected-discharge calendar feed

| Method | Path | Behaviour |
| --- | --- | --- |
| POST | `/calendar/feed` | Staff, doctors and admins. Creates a personal feed for the active department and returns its `url` once (built from `PUBLIC_APP_URL` when set). Creating a new feed revokes the previous one. Audited as `CALENDAR_FEED_CREATED`. |
| DELETE | `/calendar/feed` | Revokes your feed for the active department; audited as `CALENDAR_FEED_REVOKED` |
| GET | `/calendar/feeds/{token}.ics` | Public iCalendar (RFC 5545) for calendar apps; the random token in the URL is the credential |

The feed has one all-day event per active admission with an expected discharge date, titled `Expected discharge - Room N` and described as `Admission #id`. Patient names and identifiers are never included, because feeds sync to devices outside the application. Doctors receive only their own patients. Every fetch re-checks that the owner is enabled and still a member of the department, and `404` is returned for revoked, replaced or unknown tokens. Only a SHA-256 hash of the token is stored. Treat the URL like a password and revoke it if it is shared by mistake.
## Assistant provider check

| Method | Path | Behaviour |
| --- | --- | --- |
| GET | `/settings/ai` | Administrators only. `mode`, provider host, model, whether an API key is set (never the key) and the timeout |
| POST | `/settings/ai/test` | Administrators only; 5 per administrator per 10 minutes. Sends a fixed synthetic prompt with a single `connectionCheck` tool (no hospital or patient data) and reports `outcome`, `providerStatus` and `latencyMs`. Audited as `AI_PROVIDER_TESTED`. |

Outcomes: `COMPATIBLE` (exactly one call to the synthetic tool), `TOOL_CALLS_UNSUPPORTED`, `AUTHENTICATION_FAILED` (401/403), `PROVIDER_ERROR` (other HTTP status), `TIMEOUT`, `UNREACHABLE`, `INVALID_URL`, `INSECURE_PROTOCOL` (plain HTTP is accepted only for loopback addresses) and `NOT_EXTERNAL` (`AI_MODE` is not `external`). Provider response text is never returned.

## Rate-limit headers

Rate-limited endpoints return the remaining budget on every checked response. They are `POST /assistant/messages`, registration confirmation resends, and the API budgets below:

| Budget | Requests | Counted per | Default (`count/window`) | Variable |
| --- | --- | --- | --- | --- |
| Sign-in | `POST /auth/login` | Client address | `60/1m` | `API_RATE_LIMIT_AUTH` |
| Registration | `POST /registration/*` | Client address | `30/10m` | `API_RATE_LIMIT_REGISTRATION` |
| Search | `GET /patients`, `/admissions`, `/doctors`, `/rooms`, `/procedures`, `/audit` | Signed-in account | `300/1m` | `API_RATE_LIMIT_SEARCH` |
| Reports | `GET /reports/*` except CSV | Signed-in account | `120/1m` | `API_RATE_LIMIT_REPORTS` |
| Exports | `GET /reports/*.csv` | Signed-in account | `20/10m` | `API_RATE_LIMIT_EXPORTS` |

A budget set to `off` is not enforced, and `API_RATE_LIMITS_ENABLED=false` turns all of them off. Windows can be up to one day. Sign-in and registration budgets are checked before credentials or input, so a spent budget answers `429` even for a correct password. Client addresses are resolved the same way as for login backoff. Budgets are shared by all backend instances through the database, and the counter keys are hashed, so the `rate_windows` table holds no usernames or addresses. Every counted request adds one database write; if that is too costly, raise or turn off the search budget.

Headers on checked responses:

| Header | Meaning |
| --- | --- |
| `RateLimit-Limit` | Requests allowed in the current window |
| `RateLimit-Remaining` | Requests left in the window after this one |
| `RateLimit-Reset` | Seconds until the window resets |
| `Retry-After` | On `429` only: seconds to wait before retrying |

Clients should wait at least `Retry-After` seconds after a `429` and must not retry a rejected request sooner; the rejected request had no effect. An assistant request rejected because another one is still running returns `Retry-After: 1` without budget headers.

## Session lifetime

Sessions end after 30 minutes of inactivity and, regardless of activity, `SESSION_MAX_LIFETIME` after sign-in (default `12h`). The request that crosses the limit returns `401 SESSION_EXPIRED` and the session is invalidated; the client should fetch a new CSRF token and send the user to sign in again. Pending unsaved form input is not preserved by the server.
