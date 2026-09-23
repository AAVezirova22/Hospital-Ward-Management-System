"use client";
import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, date } from "../../api";
import type { RoomCapacity } from "../../api/contracts";
import { ErrorBox, Modal } from "../../components/workspace";

function localDateTime(value: Date) {
  return new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 16);
}

export function BedHoldManager({
  room,
  onClose,
}: {
  room: RoomCapacity;
  onClose: () => void;
}) {
  const client = useQueryClient();
  const [startsAt, setStartsAt] = useState(() => localDateTime(new Date()));
  const [endsAt, setEndsAt] = useState(() =>
    localDateTime(new Date(Date.now() + 4 * 60 * 60 * 1000)),
  );
  const [bedCount, setBedCount] = useState(1);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<Error | null>(null);
  const [busy, setBusy] = useState(false);
  const holds = room.holds ?? [];

  async function refresh() {
    await client.invalidateQueries({ queryKey: ["/rooms"] });
    await client.invalidateQueries({ queryKey: ["/reports/dashboard"] });
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api(`/rooms/${room.id}/holds`, "POST", {
        bedCount,
        reason,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
      });
      setReason("");
      await refresh();
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }

  async function release(holdId: number) {
    setBusy(true);
    setError(null);
    try {
      await api(`/rooms/${room.id}/holds/${holdId}`, "DELETE");
      await refresh();
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={`Room ${room.roomNumber} maintenance holds`} onClose={onClose}>
      <p>
        Upcoming holds reserve placement capacity as soon as they are saved and
        release it automatically at the end time.
      </p>
      <form className="form-grid" onSubmit={create}>
        <label>
          Beds to hold
          <input
            type="number"
            min="1"
            max={room.bedCount}
            required
            value={bedCount}
            onChange={(event) => setBedCount(Number(event.target.value))}
          />
        </label>
        <label className="form-full">
          Reason
          <input
            required
            maxLength={500}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="For example, equipment repair"
          />
        </label>
        <label>
          Starts
          <input
            type="datetime-local"
            required
            value={startsAt}
            onChange={(event) => setStartsAt(event.target.value)}
          />
        </label>
        <label>
          Ends
          <input
            type="datetime-local"
            required
            min={startsAt}
            value={endsAt}
            onChange={(event) => setEndsAt(event.target.value)}
          />
        </label>
        <div className="form-full">
          <ErrorBox error={error} />
        </div>
        <div className="modal-actions form-full">
          <button className="primary" disabled={busy || !reason.trim()}>
            {busy ? "Saving..." : "Save hold"}
          </button>
        </div>
      </form>
      <section className="room-hold-list" aria-label="Current and upcoming holds">
        <h3>Current and upcoming holds</h3>
        {!holds.length && <p>No current or upcoming holds.</p>}
        {holds.map((hold) => (
          <article key={hold.id}>
            <div>
              <strong>
                {hold.bedCount} bed{hold.bedCount === 1 ? "" : "s"} · {hold.reason}
              </strong>
              <small>
                {Date.parse(hold.startsAt) <= Date.now() ? "Active" : "Scheduled"}
                {" · "}{date(hold.startsAt)} to {date(hold.endsAt)}
              </small>
            </div>
            <button
              className="secondary"
              type="button"
              disabled={busy}
              onClick={() => release(hold.id)}
            >
              Release
            </button>
          </article>
        ))}
      </section>
    </Modal>
  );
}
