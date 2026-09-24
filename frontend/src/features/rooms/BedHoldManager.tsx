"use client";
import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, date } from "../../api";
import type { BedHold, RoomCapacity } from "../../api/contracts";
import { ErrorBox, Modal, useData } from "../../components/workspace";
import { CheckCircle2 } from "../../icons";

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
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  // Keep the dialog current even when a hold removes this room from a filtered page.
  const { data, error: loadError } = useData(`/rooms?roomId=${room.id}`);
  const currentRoom: RoomCapacity = data?.items?.[0] ?? room;
  const holds = currentRoom.holds ?? [];

  async function refresh() {
    await client.invalidateQueries({
      predicate: ({ queryKey }) =>
        typeof queryKey[0] === "string" &&
        /^\/(rooms|reports)(\?|\/|$)/.test(queryKey[0]),
    });
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      const hold = await api<BedHold>(`/rooms/${room.id}/holds`, "POST", {
        bedCount,
        reason,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
      });
      setNotice(
        `Hold saved: ${hold.bedCount} bed${hold.bedCount === 1 ? "" : "s"} reserved in room ${room.roomNumber}.`,
      );
      setReason("");
      await refresh();
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }

  async function release(holdId: number) {
    if (busy) return;
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      await api(`/rooms/${room.id}/holds/${holdId}`, "DELETE");
      setNotice(`Hold released in room ${room.roomNumber}.`);
      await refresh();
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title={`Room ${room.roomNumber} maintenance holds`}
      onClose={onClose}
    >
      <p>
        Upcoming holds reserve placement capacity as soon as they are saved and
        release it automatically at the end time.
      </p>
      <p>
        {currentRoom.occupiedBeds} occupied · {currentRoom.heldBeds} held /{" "}
        {currentRoom.bedCount} · {currentRoom.availableBeds} available
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
          <div className="hold-notice" role="status" aria-live="polite">
            {notice && (
              <>
                <CheckCircle2 size={19} aria-hidden="true" />
                {notice}
              </>
            )}
          </div>
          <ErrorBox error={error || loadError} />
        </div>
        <div className="modal-actions form-full">
          <button className="primary" disabled={busy || !reason.trim()}>
            {busy ? "Saving..." : "Save hold"}
          </button>
        </div>
      </form>
      <section
        className="room-hold-list"
        aria-label="Current and upcoming holds"
      >
        <h3>Current and upcoming holds</h3>
        {!holds.length && <p>No current or upcoming holds.</p>}
        {holds.map((hold) => (
          <article key={hold.id}>
            <div>
              <strong>
                {hold.bedCount} bed{hold.bedCount === 1 ? "" : "s"} ·{" "}
                {hold.reason}
              </strong>
              <small>
                {Date.parse(hold.startsAt) <= Date.now()
                  ? "Active"
                  : "Scheduled"}
                {" · "}
                {date(hold.startsAt)} to {date(hold.endsAt)}
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
