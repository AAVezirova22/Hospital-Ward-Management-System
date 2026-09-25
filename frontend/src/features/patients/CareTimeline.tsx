"use client";

import { useMemo, useState } from "react";
import type { AdmissionView } from "../../api/contracts";
import { date } from "../../api";
import { careTimeline } from "./timeline";

const eventTypes = ["admission", "transfer", "procedure", "discharge"] as const;
const localEventDate = (value: string) => {
  const at = new Date(value);
  return `${at.getFullYear()}-${String(at.getMonth() + 1).padStart(2, "0")}-${String(at.getDate()).padStart(2, "0")}`;
};

export function CareTimeline({ admissions }: { admissions: AdmissionView[] }) {
  const [kind, setKind] = useState<string>("all");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const events = useMemo(() => careTimeline(admissions), [admissions]);
  const groups = useMemo(() => {
    const byStay = new Map<string, typeof events>();
    events.forEach((event) => {
      const stay = byStay.get(event.admissionNumber) ?? [];
      stay.push(event);
      byStay.set(event.admissionNumber, stay);
    });
    return [...byStay.entries()]
      .map(([admissionNumber, stayEvents]) => ({
        admissionNumber,
        events: stayEvents
          .filter((event) => kind === "all" || event.kind === kind)
          .filter((event) => !fromDate || localEventDate(event.at) >= fromDate)
          .filter((event) => !toDate || localEventDate(event.at) <= toDate)
          .sort(
            (a, b) =>
              Date.parse(a.at) - Date.parse(b.at) || a.id.localeCompare(b.id),
          ),
      }))
      .filter((group) => group.events.length > 0)
      .sort(
        (a, b) => Date.parse(a.events[0].at) - Date.parse(b.events[0].at),
      );
  }, [events, fromDate, kind, toDate]);

  return (
    <section className="panel care-timeline" aria-labelledby="care-timeline-title">
      <h2 id="care-timeline-title">Care journey</h2>
      <div className="care-timeline-filters" aria-label="Filter care events">
        <label>
          Event type
          <select value={kind} onChange={(event) => setKind(event.target.value)}>
            <option value="all">All event types</option>
            {eventTypes.map((type) => (
              <option key={type} value={type}>
                {type[0].toUpperCase() + type.slice(1)}
              </option>
            ))}
          </select>
        </label>
        <label>
          From date
          <input
            type="date"
            value={fromDate}
            onChange={(event) => setFromDate(event.target.value)}
          />
        </label>
        <label>
          To date
          <input
            type="date"
            value={toDate}
            onChange={(event) => setToDate(event.target.value)}
          />
        </label>
      </div>
      {groups.length ? (
        groups.map(({ admissionNumber, events: stayEvents }) => {
          const stay = admissions.find(
            (item) => item.admission.admissionNumber === admissionNumber,
          );
          const active = stay?.admission.status === "ACTIVE";
          return (
            <section className="care-timeline-stay" key={admissionNumber}>
              <h3>Stay {admissionNumber}</h3>
              <ol aria-label={`Events for stay ${admissionNumber}`}>
                {stayEvents.map((event) => (
                  <li key={event.id} className={`care-event ${event.kind}`}>
                    <time dateTime={event.at}>{date(event.at)}</time>
                    <div>
                      <strong>{event.title}</strong>
                      <p>{event.detail}</p>
                    </div>
                  </li>
                ))}
                {active && kind === "all" && !fromDate && !toDate && (
                  <li className="care-event pending">
                    <span>Next step</span>
                    <div>
                      <strong>
                        {stay.admission.expectedDischargeDate
                          ? "Discharge planned"
                          : "Care in progress"}
                      </strong>
                      <p>
                        {stay.admission.expectedDischargeDate
                          ? `Expected ${stay.admission.expectedDischargeDate} · subject to confirmation`
                          : "No discharge date scheduled"}
                      </p>
                    </div>
                  </li>
                )}
              </ol>
            </section>
          );
        })
      ) : (
        <p className="care-timeline-empty" role="status" aria-live="polite">
          {admissions.length
            ? "No care events match these filters."
            : "No care events recorded yet."}
        </p>
      )}
    </section>
  );
}
