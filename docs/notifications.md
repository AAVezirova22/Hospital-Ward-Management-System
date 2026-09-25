# Notifications

Server-side inbox for staff (`ADMIN`, `MEDICAL_STAFF`, `DOCTOR`), scoped to the open department.

| Issue | What the backend does |
| --- | --- |
| #303 | Capacity alerts and recent-action notices are stored in `notifications` and survive reloads. Recent actions are personal to the account that made the change and expire after the policy retention. |
| #304 | Read state is per account (`notification_reads`). Mark one read or unread, or mark everything read. |
| #317 | One open alert per condition (`capacity:room:{id}`, `capacity:department`), enforced by a partial unique index. It is updated in place and resolved when the condition clears. A worse severity makes it unread again. |
| #310 | Alerts at or above the policy severity get an acknowledgement deadline. Staff acknowledge them; unacknowledged alerts are escalated once to the policy role, become unread again, and write a `NOTIFICATION_ESCALATED` audit row with source `SYSTEM`. |
| #320 | Per-department policy: categories, warning/critical thresholds, escalation severity, deadline, role and retention. Channels are `IN_APP` only. |

## API (`/api/v1/notifications`)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/` | `page`, `size` (max 100), `category` (`CAPACITY`, `ACTIVITY`), `status` (`OPEN`, `RESOLVED`, `INFO`), `unreadOnly`. Escalated open alerts sort first. |
| GET | `/counts` | `unread`, `unreadAlerts`, `awaitingAcknowledgement`, `escalatedToYou` |
| GET | `/{id}` | 404 outside the department or for another account's notice |
| POST / DELETE | `/{id}/read` | Mark read / unread |
| POST | `/read-all` | Optional `category` |
| POST | `/{id}/acknowledge` | `ADMIN` or `MEDICAL_STAFF`. 409 `NOT_ACKNOWLEDGEABLE`, `ALREADY_ACKNOWLEDGED`, `NOTIFICATION_RESOLVED` |
| GET | `/policy` | Defaults (`configured: false`, `version: 0`) until saved |
| PUT | `/policy` | `ADMIN`. Send the version you read; 409 `STALE_STATE` if it changed. 400 `INVALID_POLICY` if warning ≥ critical |

## How alerts are produced

- After an audited change that can affect beds commits, the department is re-evaluated (`NotificationEvents`).
- A sweep every 60 s re-evaluates every department, escalates overdue alerts and deletes expired rows.
  Settings: `app.notifications.sweep-enabled`, `app.notifications.sweep-interval-ms`,
  `app.notifications.sweep-initial-delay-ms`. Defaults for thresholds come from `app.operations.warning-percent`
  and `app.operations.critical-percent`.
- Evaluation per department is serialized with a Postgres advisory lock, not the clinical workflow lock.

## Not included yet

- Frontend: `NotificationCenter` still derives alerts in the browser. Switching it to this API is a separate change.
- Email delivery waits for the reminder mailer (#453). Browser push for opted-in staff is available through the task reminder preferences and subscriptions API; see [the API guide](api.md#clinician-task-reminders-and-browser-push).
- Bed holds (#454): once merged, held beds should count as unavailable in the capacity calculation.
