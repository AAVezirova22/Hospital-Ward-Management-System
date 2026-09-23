# REST API

All paths start with `/api/v1`. Except health, login and CSRF-token retrieval, endpoints require a session. POST/PUT operations require the session's CSRF token. JSON request validation rejects unknown fields. Errors return `timestamp`, `status`, `code`, `message`, `path`. Send `X-Department-Id` to open a joined department; the last selected department is also stored on the session.

## Core endpoints

| Method | Path | Request / result |
| --- | --- | --- |
| GET | `/health` | Process health |
| GET | `/auth/csrf` | `{token, headerName}` |
| POST | `/auth/login` | Form-encoded `username`, `password`; returns account without hash |
| GET | `/auth/me` | Current account |
| POST | `/auth/logout` | Invalidates session, returns 204 |
| GET | `/patients?q=...` | Scoped demographic matches |
| GET | `/patients/{id}` | Patient plus scoped admission/room/procedure history |
| POST / PUT | `/patients` / `/patients/{id}` | PatientInput |
| GET / POST / PUT | `/doctors` / `/doctors/{id}` | DoctorInput; writes admin-only |
| GET / POST / PUT | `/rooms` / `/rooms/{id}` | RoomInput; GET supports `minFree` and repeated `requiredCapabilities` tags |
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

Room `capabilities` and admission `requiredRoomCapabilities` are arrays of up to 30 tags; each tag is trimmed, lowercased and limited to 64 characters. A room must support every requirement when an admission or transfer is saved. Requirements remain attached to an admission for future transfers. Room search filters on every requested tag; the assistant also returns excluded rooms with missing-tag or availability reasons. Removing a capability required by an active occupant returns `409 ROOM_CAPABILITY_IN_USE`.

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
| 429 | `AI_RATE_LIMIT` | Assistant quota or in-flight request limit |
