"use client";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, date, activeDepartment, type Row } from "../../api";
import { ErrorBox, Empty, Title } from "../../components/workspace";

type AuditPage = {
  page: number;
  size: number;
  total: number;
  events: Row[];
};

export function Audit() {
  const [page, setPage] = useState(0);
  const [eventType, setEventType] = useState("");
  const { data, error, isLoading } = useQuery({
    queryKey: ["/audit", activeDepartment(), page, eventType],
    queryFn: () =>
      api<AuditPage>(
        `/audit?page=${page}&size=50${eventType ? `&eventType=${encodeURIComponent(eventType)}` : ""}`,
      ),
  });
  const events = data?.events ?? [];
  return (
    <>
      <Title
        eyebrow="Accountability"
        title="A trace of every change."
        description="Paged audit events for this department. Patient notes and passwords are excluded."
      />
      <div className="toolbar">
        <input
          aria-label="Filter by event type"
          placeholder="Event type"
          value={eventType}
          onChange={(e) => {
            setEventType(e.target.value);
            setPage(0);
          }}
        />
        <span>{data?.total ?? 0} events</span>
      </div>
      <ErrorBox error={error} />
      <section className="panel table-panel">
        <table>
          <thead>
            <tr>
              <th scope="col">Timestamp</th>
              <th scope="col">Event</th>
              <th scope="col">Entity</th>
              <th scope="col">User ID</th>
              <th scope="col">Source</th>
              <th scope="col">Metadata</th>
            </tr>
          </thead>
          <tbody>
            {events.map((a) => (
              <tr key={String(a.id)}>
                <td>{date(String(a.timestamp ?? ""))}</td>
                <td className="mono">{a.eventType}</td>
                <td>
                  {a.entityType} #{a.entityId}
                </td>
                <td>{a.userId}</td>
                <td>{a.source}</td>
                <td className="mono">{a.metadata}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {isLoading && <div className="skeleton">Loading audit trail…</div>}
        {!isLoading && events.length === 0 && <Empty text="No audit events on this page." />}
        <div className="actions">
          <button className="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <button
            className="secondary"
            disabled={!data || (page + 1) * data.size >= data.total}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      </section>
    </>
  );
}
