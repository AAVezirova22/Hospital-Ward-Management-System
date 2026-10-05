# Doctor appointments

Open **Doctors → Appointments**, or use **Appointments** on a doctor's directory row. Select a doctor to see their schedule and book with a full name, future date/time and duration. Contact details and notes are optional. Staff and administrators can book and cancel; a doctor can view only their own schedule. Patient accounts cannot use this staff workflow.

The schedule supports doctor, date, status and name filters, pagination, live refresh, and cancellation history. Cancelling releases the slot without deleting the booking or its audit record. Appointments are independent of inpatient admissions and do not require creating a patient record.

## Availability and time

The date/time field uses the **department time zone**, displayed next to the form. An API caller may supply an ISO date/time with an explicit UTC offset instead. Booking rejects past times, inactive doctors, and overlapping scheduled appointments, including concurrent booking attempts. Adjacent appointments are allowed. Default duration is 30 minutes; supported duration is 5–240 minutes. Availability checks do not reserve anything. Working hours, leave calendars, reminders, payments and rescheduling are outside this feature; rescheduling requires cancelling and booking again.

The current local demo department is configured as UTC. A sender in Bulgaria should specify `Europe/Sofia` or an explicit offset if they mean Bulgarian local time. Do not assume the Mac's time zone changes the department setting. Ambiguous or nonexistent daylight-saving times require a different time or an explicit offset.

Doctors with future scheduled appointments cannot be deactivated until those bookings are cancelled.

## Operations assistant

The assistant exposes `getDoctorAppointments`, `getDoctorAvailability` and `prepareAppointment`. Preparing produces an owned, expiring confirmation card; confirmation rechecks availability before saving. Local command examples:

```text
Appointments with Elena Dimitrova
Check availability with Elena Dimitrova on 2026-11-02T16:30Z
Book appointment with Elena Dimitrova on 2026-11-02T16:30Z for Aleksandar Kolev
```

The optional suffix `duration 45` changes the duration. External language models can use the same validated tools. Confirmation is required in the application assistant.

## Existing OpenClaw iMessage setup

The owner's Mac already has an OpenClaw iMessage gateway and an approved sender. OpenClaw can use its authenticated browser profile to operate this same appointment form at `http://127.0.0.1:8088`. The workflow collects the doctor, a full future date/time with a clear time zone, and the booking name, checks availability, books, then verifies the saved receipt. A browser session must be signed in as authorized staff. This uses the owner's local demo session; a sender-to-Medcore-account integration is not included. See [iMessage integration](imessage-integration.md).

Keep the Docker application and gateway running:

```sh
docker compose up -d --build
openclaw gateway run
```

If the gateway is already running, use `openclaw gateway status` and `openclaw channels status --probe --channel imessage` to check it. A fresh sender message is needed after recovery because stale inbound backlog is suppressed. OpenClaw model-provider billing and gateway uptime remain external dependencies.

## API

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/appointments` | Paged schedule; optional `doctorId`, `from`, `to`, `status`, `q`, `page`, `size` |
| `GET /api/v1/doctors/{id}/availability?startsAt=…&durationMinutes=30` | Check an active doctor's proposed interval |
| `POST /api/v1/appointments` | Book with `doctorId`, `attendeeName`, `startsAt`, optional `durationMinutes`, `contact`, `notes` |
| `POST /api/v1/appointments/{id}/cancel` | Cancel with the current `{ "version": … }` |

Writes require the existing authenticated session and CSRF token. Booking and cancellation support `Idempotency-Key` retries. Records and queries are department-scoped. Creation and cancellation use the shared workflow lock and emit `APPOINTMENT_BOOKED` / `APPOINTMENT_CANCELLED` audit events.

## Verification

`AppointmentIntegrationTest` covers overlap boundaries, concurrent requests, cancellation, retries, roles, department isolation, inactive doctors, date/time validation, DST, proposal ownership, expiry and confirmation-time conflicts. Frontend unit tests cover appointment response validation and time-zone conversion. `frontend/e2e/appointments-live.spec.ts` exercises real Docker booking, persistence, visible conflict errors, cancellation, mobile layout, and assistant confirmation, cancelling only its synthetic reservations afterward.
