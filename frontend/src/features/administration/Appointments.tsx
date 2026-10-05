"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, activeDepartment, fullName } from "../../api";
import type {
  AppointmentPage,
  Doctor,
  DoctorAppointment,
  DoctorAvailability,
} from "../../api/contracts";
import {
  ErrorBox,
  Empty,
  Status,
  useAllPages,
  useData,
  useUser,
} from "../../components/workspace";
import { appointmentTime, dateTimeInTimeZone } from "../../date-time";
import { CheckCircle2 } from "../../icons";

export function Appointments({
  doctorId,
  onDoctorChange,
}: {
  doctorId: string;
  onDoctorChange: (value: string) => void;
}) {
  const user = useUser();
  const canBook = user.role === "ADMIN" || user.role === "MEDICAL_STAFF";
  const doctors = useAllPages<Doctor>("/doctors");
  const workspace = useData("/workspaces");
  const timeZone = workspace.data?.timeZone || "UTC";
  const visibleDoctors = (doctors.data || []).filter(
    (d) => user.role !== "DOCTOR" || d.id === user.doctorId,
  );
  const selected =
    user.role === "DOCTOR" ? String(user.doctorId || "") : doctorId;
  const [status, setStatus] = useState("SCHEDULED");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [attendeeName, setAttendeeName] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [durationMinutes, setDurationMinutes] = useState(30);
  const [contact, setContact] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<Error | null>(null);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [availability, setAvailability] = useState<DoctorAvailability | null>(
    null,
  );
  const [cancelId, setCancelId] = useState<number | null>(null);
  const client = useQueryClient();
  const params = new URLSearchParams({ status, q: query, page: String(page) });
  if (selected) params.set("doctorId", selected);
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const path = `/appointments?${params}`;
  const schedule = useQuery<AppointmentPage>({
    queryKey: [path, activeDepartment()],
    queryFn: () => api<AppointmentPage>(path),
    refetchInterval: 15_000,
  });
  const result = schedule.data?.appointments;

  useEffect(() => {
    if (workspace.data?.timeZone) {
      setStartsAt(
        dateTimeInTimeZone(
          new Date(Date.now() + 60 * 60_000),
          workspace.data.timeZone,
        ),
      );
    }
  }, [workspace.data?.activeDepartmentId, workspace.data?.timeZone]);
  useEffect(() => {
    setAvailability(null);
    setError(null);
    setNotice("");
    setPage(0);
    setCancelId(null);
  }, [selected]);
  useEffect(() => {
    setAvailability(null);
  }, [startsAt, durationMinutes]);

  async function refresh() {
    await client.invalidateQueries({
      predicate: ({ queryKey }) =>
        typeof queryKey[0] === "string" &&
        queryKey[0].startsWith("/appointments"),
    });
  }

  async function checkAvailability() {
    if (!selected || !startsAt || busy) return;
    setBusy(true);
    setError(null);
    setNotice("");
    setAvailability(null);
    const checkedDoctor = selected;
    const checkedStart = startsAt;
    const checkedDuration = durationMinutes;
    try {
      const params = new URLSearchParams({
        startsAt: checkedStart,
        durationMinutes: String(checkedDuration),
      });
      setAvailability(
        await api<DoctorAvailability>(
          `/doctors/${checkedDoctor}/availability?${params}`,
        ),
      );
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }

  async function book(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !selected) return;
    setBusy(true);
    setError(null);
    setNotice("");
    setAvailability(null);
    try {
      const saved = await api<DoctorAppointment>("/appointments", "POST", {
        doctorId: Number(selected),
        attendeeName,
        startsAt,
        durationMinutes,
        contact,
        notes,
      });
      setNotice(
        `Appointment #${saved.id} booked for ${saved.attendeeName} with Dr. ${fullName(saved.doctor)} on ${appointmentTime(saved.startsAt, saved.timeZone)} (${saved.timeZone}), ${saved.durationMinutes} minutes.`,
      );
      setAttendeeName("");
      setContact("");
      setNotes("");
      setStatus("SCHEDULED");
      setFrom("");
      setTo("");
      setQuery("");
      setPage(0);
      await refresh();
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }

  async function cancel(appointment: DoctorAppointment) {
    if (busy) return;
    setBusy(true);
    setError(null);
    setNotice("");
    setAvailability(null);
    try {
      await api(`/appointments/${appointment.id}/cancel`, "POST", {
        version: appointment.version,
      });
      setNotice(
        `Appointment #${appointment.id} for ${appointment.attendeeName} cancelled. The time is available to book again.`,
      );
      setCancelId(null);
      await refresh();
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="appointment-workspace">
      <section
        className="panel appointment-schedule"
        aria-label="Doctor appointments"
      >
        <div className="appointment-heading">
          <div>
            <h2>Doctor appointments</h2>
            <p>
              All times in {timeZone}. Appointment windows cannot overlap for
              the same doctor.
            </p>
          </div>
          <strong className="mono">
            {result?.totalElements ?? "—"} appointments
          </strong>
        </div>
        <div className="appointment-filters">
          <label>
            Doctor
            <select
              value={selected}
              disabled={busy || user.role === "DOCTOR"}
              onChange={(e) => onDoctorChange(e.target.value)}
            >
              <option value="">All doctors</option>
              {visibleDoctors.map((d) => (
                <option key={d.id} value={d.id}>
                  Dr. {fullName(d)}
                  {d.active ? "" : " (inactive)"}
                </option>
              ))}
            </select>
          </label>
          <label>
            Status
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="SCHEDULED">Scheduled</option>
              <option value="CANCELLED">Cancelled</option>
              <option value="ALL">All appointments</option>
            </select>
          </label>
          <label>
            From date
            <input
              type="date"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <label>
            To date
            <input
              type="date"
              min={from}
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <label className="appointment-search">
            Booking name
            <input
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setPage(0);
              }}
              placeholder="Search appointments"
            />
          </label>
        </div>
        <ErrorBox error={schedule.error || doctors.error || workspace.error} />
        {schedule.isLoading ? (
          <div className="skeleton">Loading appointments…</div>
        ) : result && !result.items.length ? (
          <Empty text="No appointments match these filters. Choose a doctor to book a future appointment." />
        ) : (
          <div className="appointment-table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Date and time</th>
                  <th scope="col">Booking</th>
                  <th scope="col">Doctor</th>
                  <th scope="col">Status</th>
                  {canBook && <th scope="col">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {result?.items.map((a) => (
                  <tr key={a.id}>
                    <td>
                      <strong>{appointmentTime(a.startsAt, a.timeZone)}</strong>
                      <small>
                        {a.durationMinutes} minutes · {a.timeZone}
                      </small>
                    </td>
                    <td>
                      <strong>{a.attendeeName}</strong>
                      <small>
                        #{a.id}
                        {a.contact ? ` · ${a.contact}` : ""}
                      </small>
                      {a.notes && <small>{a.notes}</small>}
                    </td>
                    <td>
                      <strong>Dr. {fullName(a.doctor)}</strong>
                      <small>{a.doctor.specialty}</small>
                    </td>
                    <td>
                      <Status value={a.status} />
                    </td>
                    {canBook && (
                      <td>
                        {a.status === "SCHEDULED" &&
                          (cancelId === a.id ? (
                            <div className="appointment-cancel-review">
                              <span>
                                Cancel #{a.id} for {a.attendeeName}?
                              </span>
                              <button
                                className="secondary"
                                disabled={busy}
                                onClick={() => cancel(a)}
                              >
                                Confirm cancellation
                              </button>
                              <button
                                className="text-button"
                                disabled={busy}
                                onClick={() => setCancelId(null)}
                              >
                                Keep appointment
                              </button>
                            </div>
                          ) : (
                            <button
                              className="text-button"
                              disabled={busy}
                              onClick={() => {
                                setCancelId(a.id);
                                setNotice("");
                                setError(null);
                              }}
                            >
                              Cancel appointment
                            </button>
                          ))}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {result && result.totalPages > 0 && (
          <div
            className="toolbar"
            role="navigation"
            aria-label="Appointment pages"
          >
            <button
              className="secondary"
              disabled={result.page === 0}
              onClick={() => setPage(result.page - 1)}
            >
              Previous
            </button>
            <span>
              Page {result.page + 1} of {result.totalPages} ·{" "}
              {result.totalElements} appointments
            </span>
            <button
              className="secondary"
              disabled={!result.hasNext}
              onClick={() => setPage(result.nextPage!)}
            >
              Next
            </button>
          </div>
        )}
      </section>
      {canBook && (
        <section
          className="panel appointment-booking"
          aria-label="Book an appointment"
        >
          <h2>Book an appointment</h2>
          <p>
            Select a doctor from the schedule filter. Booking reserves the
            complete appointment window.
          </p>
          <form className="form-grid" onSubmit={book}>
            <fieldset
              disabled={
                busy ||
                !selected ||
                !workspace.data?.timeZone ||
                !visibleDoctors.find((d) => String(d.id) === selected)?.active
              }
              className="appointment-fields form-full"
            >
              <label className="form-full">
                Full name
                <input
                  required
                  maxLength={120}
                  value={attendeeName}
                  onChange={(e) => setAttendeeName(e.target.value)}
                  placeholder="Name to book under"
                />
              </label>
              <label className="form-full">
                Date and time ({timeZone})
                <input
                  type="datetime-local"
                  required
                  value={startsAt}
                  onChange={(e) => setStartsAt(e.target.value)}
                />
              </label>
              <label>
                Duration (minutes)
                <input
                  type="number"
                  min={5}
                  max={240}
                  required
                  value={durationMinutes}
                  onChange={(e) => setDurationMinutes(Number(e.target.value))}
                />
              </label>
              <label>
                Contact (optional)
                <input
                  maxLength={120}
                  value={contact}
                  onChange={(e) => setContact(e.target.value)}
                />
              </label>
              <label className="form-full">
                Notes (optional)
                <textarea
                  rows={3}
                  maxLength={500}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                />
              </label>
              <div className="actions form-full">
                <button
                  type="button"
                  className="secondary"
                  disabled={
                    !startsAt || durationMinutes < 5 || durationMinutes > 240
                  }
                  onClick={checkAvailability}
                >
                  Check availability
                </button>
                <button
                  className="primary"
                  disabled={!attendeeName.trim() || !startsAt}
                >
                  Book appointment
                </button>
              </div>
            </fieldset>
          </form>
          {!selected && (
            <p className="muted">Choose a doctor to enable booking.</p>
          )}
          {selected &&
            visibleDoctors.find((d) => String(d.id) === selected)?.active ===
              false && (
              <p className="muted">
                This doctor is inactive. Select an active doctor to book.
              </p>
            )}
          {availability && (
            <p
              role="status"
              className={
                availability.available ? "appointment-available" : "error"
              }
            >
              {availability.available ? "Available" : "Unavailable"}:{" "}
              {availability.reason} {availability.durationMinutes} minutes in{" "}
              {availability.timeZone}. Availability is rechecked when booking.
            </p>
          )}
        </section>
      )}
      <div className="appointment-feedback">
        {notice && (
          <div className="hold-notice" role="status" aria-live="polite">
            <CheckCircle2 size={19} aria-hidden="true" />
            {notice}
          </div>
        )}
        <ErrorBox error={error} />
      </div>
    </div>
  );
}
