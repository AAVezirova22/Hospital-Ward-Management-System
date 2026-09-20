"use client";
import { ArrowRight } from "lucide-react";
import type { PlannedTransfer } from "./model";

export function PlanReview({
  plan,
  review,
  busy,
  stale,
  conflicts,
  loadError,
  roomName,
  onRemove,
  onDiscard,
  onConfirm,
}: {
  plan: PlannedTransfer[];
  review: boolean;
  busy: boolean;
  stale: boolean;
  conflicts: boolean;
  loadError: boolean;
  roomName: (id: number) => string;
  onRemove: (admissionId: number) => void;
  onDiscard: () => void;
  onConfirm: () => void;
}) {
  if (plan.length === 0) return null;
  return (
    <section className="panel plan-review">
      <h2>{review ? "Review your plan" : "Staged transfers"}</h2>
      <p>
        The attending doctor stays unchanged. Each successful transfer creates a
        ROOM_TRANSFERRED audit event.
      </p>
      {stale && (
        <p className="error" role="alert">
          A staged admission changed. Remove it and prepare a new transfer.
        </p>
      )}
      {conflicts && (
        <p className="error" role="alert">
          Capacity changed. Revise the plan before confirming.
        </p>
      )}
      {plan.map((p) => (
        <div className="planned-transfer" key={p.admissionId}>
          <strong>{p.patientName}</strong>
          <span>
            Room {roomName(p.fromRoomId)} <ArrowRight size={16} /> Room{" "}
            {roomName(p.toRoomId)}
          </span>
          <button
            className="text-button"
            disabled={busy}
            onClick={() => onRemove(p.admissionId)}
          >
            Remove
          </button>
        </div>
      ))}
      {review && (
        <p>
          Transfers apply one at a time. If capacity or permissions change,
          processing stops; completed transfers remain saved.
        </p>
      )}
      <div className="actions">
        <button className="secondary" disabled={busy} onClick={onDiscard}>
          Discard plan
        </button>
        <button
          className="primary"
          disabled={busy || stale || conflicts || loadError}
          onClick={onConfirm}
        >
          {busy
            ? "Applying plan…"
            : review
              ? "Confirm transfers"
              : "Review plan"}
        </button>
      </div>
    </section>
  );
}
