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

function normalizeMetadata(raw: string): { entries: [string, string][]; json: string; legacy: boolean } {
  try {
    const parsed: unknown = JSON.parse(raw);
    const fields = parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? Object.entries(parsed as Record<string, unknown>).map(([key, value]) => [key, typeof value === "string" ? value : JSON.stringify(value)] as [string, string])
      : [["value", JSON.stringify(parsed)] as [string, string]];
    return { entries: fields, json: JSON.stringify(parsed, null, 2), legacy: false };
  } catch {
    const body = raw.startsWith("{") && raw.endsWith("}") ? raw.slice(1, -1) : raw;
    const entries: Record<string, string> = {};
    for (const part of body.split(/,\s*(?=[A-Za-z0-9_.-]+=)/)) {
      const separator = part.indexOf("=");
      if (separator > 0) entries[part.slice(0, separator).trim()] = part.slice(separator + 1).trim();
    }
    return {
      entries: Object.keys(entries).length ? Object.entries(entries) : [["Legacy metadata (unparsed)", raw]],
      json: JSON.stringify({ format: "legacy-map-string", entries, raw }, null, 2),
      legacy: true,
    };
  }
}

export function Audit() {
  const [page, setPage] = useState(0);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [copyErrorId, setCopyErrorId] = useState<string | null>(null);
  const [eventType, setEventType] = useState("");
  const [actorId, setActorId] = useState("");
  const [entityType, setEntityType] = useState("");
  const [entityId, setEntityId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const filters = { eventType, actorId, entityType, entityId, from, to };
  const hasFilters = Object.values(filters).some(Boolean);
  const query = new URLSearchParams({ page: String(page), size: "50" });
  if (eventType.trim()) query.set("eventType", eventType.trim());
  if (actorId) query.set("actorId", actorId);
  if (entityType.trim()) query.set("entityType", entityType.trim());
  if (entityId) query.set("entityId", entityId);
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  const { data, error, isLoading } = useQuery({
    queryKey: [
      "/audit",
      activeDepartment(),
      page,
      eventType,
      actorId,
      entityType,
      entityId,
      from,
      to,
    ],
    queryFn: () => api<AuditPage>(`/audit?${query.toString()}`),
  });
  const events = data?.events ?? [];
  const copyMetadata = async (id: string, metadata: unknown) => {
    setCopyErrorId(null);
    try {
      await navigator.clipboard.writeText(normalizeMetadata(String(metadata ?? "")).json);
      setCopiedId(id);
      window.setTimeout(() => setCopiedId((current) => current === id ? null : current), 1800);
    } catch {
      setCopyErrorId(id);
    }
  };
  return (
    <>
      <Title
        eyebrow="Accountability"
        title="A trace of every change."
        description="Paged audit events for this department. Patient notes and passwords are excluded."
      />
      <div className="toolbar">
        <label>
          Event type
          <input
            value={eventType}
            onChange={(e) => {
              setEventType(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          Actor user ID
          <input
            type="number"
            min="1"
            step="1"
            inputMode="numeric"
            value={actorId}
            onChange={(e) => {
              setActorId(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          Entity type
          <input
            value={entityType}
            onChange={(e) => {
              setEntityType(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          Entity ID
          <input
            type="number"
            min="1"
            step="1"
            inputMode="numeric"
            value={entityId}
            onChange={(e) => {
              setEntityId(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          From date
          <input
            type="date"
            max={to || undefined}
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
            min={from || undefined}
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setPage(0);
            }}
          />
        </label>
        {hasFilters && (
          <button
            className="secondary"
            onClick={() => {
              setEventType("");
              setActorId("");
              setEntityType("");
              setEntityId("");
              setFrom("");
              setTo("");
              setPage(0);
            }}
          >
            Clear all filters
          </button>
        )}
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
                <td>
                  <details>
                    <summary aria-label={`View metadata for audit event ${a.id}`}>View metadata</summary>
                    <dl>
                      {normalizeMetadata(String(a.metadata ?? "")).entries.map(([key, value]) => (
                        <div key={key}>
                          <dt>{key}</dt>
                          <dd className="mono" style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{value}</dd>
                        </div>
                      ))}
                    </dl>
                    <button className="secondary" onClick={() => void copyMetadata(String(a.id), a.metadata)}>
                      {copiedId === String(a.id) ? "Copied JSON" : "Copy JSON"}
                    </button>
                    {copyErrorId === String(a.id) && <small role="alert">Could not copy. Check clipboard access and try again.</small>}
                    {normalizeMetadata(String(a.metadata ?? "")).legacy && (
                      <small>Legacy map text is split into key/value fields where possible. The copied JSON preserves the exact original text.</small>
                    )}
                  </details>
                </td>
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
