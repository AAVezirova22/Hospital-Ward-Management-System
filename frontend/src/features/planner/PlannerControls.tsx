"use client";
import { ArrowRight } from "../../icons";
import { api, fullName } from "../../api";
import type { AdmissionView, ArrivalPlan, RoomCapacity } from "../../api/contracts";
import { missingCapabilities } from "../../room-capabilities";

export function PlannerControls({
  canWrite,
  busy,
  loadError,
  selected,
  destination,
  bedIdentifier,
  rooms,
  active,
  current,
  roomName,
  dischargeDate,
  timeZone,
  today,
  arrivals,
  simulation,
  onSelect,
  onDestination,
  onBedIdentifier,
  onStage,
  onDischargeDate,
  onSchedule,
  onArrivals,
  onSimulation,
  onBusy,
  onError,
}: {
  canWrite: boolean;
  busy: boolean;
  loadError: boolean;
  selected?: number;
  destination: string;
  bedIdentifier: string;
  rooms: RoomCapacity[];
  active: AdmissionView[];
  current?: AdmissionView;
  roomName: (id: number) => string;
  dischargeDate: string;
  timeZone: string;
  today: string;
  arrivals: number;
  simulation?: ArrivalPlan;
  onSelect: (id: number | undefined, discharge?: string) => void;
  onDestination: (value: string) => void;
  onBedIdentifier: (value: string) => void;
  onStage: (id: number, roomId: number) => void;
  onDischargeDate: (value: string) => void;
  onSchedule: () => void;
  onArrivals: (value: number) => void;
  onSimulation: (plan?: ArrivalPlan) => void;
  onBusy: (value: boolean) => void;
  onError: (value: string) => void;
}) {
  const requiredRoomCapabilities = current?.admission.requiredRoomCapabilities ?? [];
  const compatibleRooms = rooms.filter(
    (room) =>
      missingCapabilities(requiredRoomCapabilities, room.capabilities).length === 0,
  );
  const placeableRooms = compatibleRooms.filter(
    (room) =>
      room.active &&
      room.availableBeds > 0 &&
      room.id !== current?.assignment?.roomId,
  );
  const excludedRooms = rooms.filter(
    (room) =>
      missingCapabilities(requiredRoomCapabilities, room.capabilities).length > 0,
  );
  const destinationRoom = rooms.find((room) => room.id === Number(destination));
  const unavailableBeds = new Set([
    ...active.flatMap((view) => view.rooms.filter((entry) => !entry.assignment.releasedAt && entry.assignment.roomId === destinationRoom?.id)
      .map((entry) => entry.assignment.bedIdentifier)),
  ]);
  const availableBeds = (destinationRoom?.bedIdentifiers ?? []).filter((id) => !unavailableBeds.has(id));
  return (
    <aside className="panel planner-controls">
      <h2>{canWrite ? "Plan a placement" : "Ward overview"}</h2>
      <p>Held beds stay out of current and upcoming placement capacity.</p>
      {canWrite ? (
        <>
          <label>
            Patient
            <select
              aria-label="Patient"
              value={selected ?? ""}
              disabled={busy}
              onChange={(e) => {
                const id = Number(e.target.value) || undefined;
                onSelect(
                  id,
                  active.find((a) => a.admission.id === id)?.admission
                    .expectedDischargeDate ?? "",
                );
              }}
            >
              <option value="">Choose an active patient</option>
              {active.map((v) => (
                <option key={v.admission.id} value={v.admission.id}>
                  {fullName(v.patient)}
                </option>
              ))}
            </select>
          </label>
          {current && (
            <p>
              Dr. {fullName(current.doctor)}
              <br />
              Currently Room {roomName(current.assignment?.roomId ?? 0)}
            </p>
          )}
          <label>
            Destination
            <select
              aria-label="Destination"
              value={destination}
              disabled={busy}
              onChange={(e) => onDestination(e.target.value)}
            >
              <option value="">Choose a room</option>
                  {placeableRooms.map((r) => (
                    <option
                      key={r.id}
                      value={r.id}
                      disabled={!r.active || r.availableBeds <= 0}
                    >
                      Room {r.roomNumber} ·{" "}
                      {r.active ? `${r.availableBeds} free now` : "Inactive"}
                    </option>
                  ))}
            </select>
          </label>
          {destinationRoom && (
            <label>
              Destination bed
              <select aria-label="Destination bed" value={bedIdentifier}
                disabled={busy} onChange={(e) => onBedIdentifier(e.target.value)}>
                <option value="">Choose an available bed</option>
                {availableBeds.map((id) => <option value={id} key={id}>{id}</option>)}
              </select>
            </label>
          )}
          {current && placeableRooms.length === 0 && (
            <p role="status">
              {requiredRoomCapabilities.length > 0
                ? "No active room with free capacity supports every required capability."
                : "No active room has free capacity."}
            </p>
          )}
          {requiredRoomCapabilities.length > 0 && (
            <div role="status" aria-live="polite">
              <p>Every destination must support: {requiredRoomCapabilities.join(", ")}.</p>
              {excludedRooms.length > 0 ? (
                <ul>
                  {excludedRooms.map((room) => (
                    <li key={room.id}>
                      Room {room.roomNumber} excluded: missing {missingCapabilities(requiredRoomCapabilities, room.capabilities).join(", ")}.
                    </li>
                  ))}
                </ul>
              ) : (
                <p>No rooms are excluded for capability requirements.</p>
              )}
            </div>
          )}
          <button
            className="primary"
            disabled={!selected || !destination || !bedIdentifier || busy || loadError}
            onClick={() => onStage(selected!, Number(destination))}
          >
            Preview transfer
            <ArrowRight size={16} />
          </button>
          <p className="muted">
            You can also drag a patient onto a room. All changes stay in
            simulation until confirmed.
          </p>
          {current && (
            <details>
              <summary>Plan discharge date</summary>
              <label>
                Expected discharge ({timeZone})
                <input
                  type="date"
                  min={today}
                  value={dischargeDate}
                  onChange={(e) => onDischargeDate(e.target.value)}
                />
              </label>
              <p>
                Currently:{" "}
                {current.admission.expectedDischargeDate ?? "Not scheduled"}
              </p>
              <button className="secondary" disabled={busy} onClick={onSchedule}>
                Save expected date
              </button>
            </details>
          )}
        </>
      ) : (
        <p>
          Your doctor role can view assigned patients and simulate arrivals.
          Transfers require medical staff or an administrator.
        </p>
      )}
      <details open>
        <summary>What if patients arrive?</summary>
        <label>
          Additional arrivals
          <input
            type="number"
            min="0"
            max="100"
            value={arrivals}
            onChange={(e) => onArrivals(Number(e.target.value))}
          />
        </label>
        <button
          className="secondary"
          disabled={
            busy || !Number.isInteger(arrivals) || arrivals < 0 || arrivals > 100
          }
          onClick={async () => {
            onBusy(true);
            onError("");
            try {
              onSimulation(
                await api<ArrivalPlan>(`/planner/simulate?arrivals=${arrivals}`),
              );
            } catch (e) {
              onError((e as Error).message);
            } finally {
              onBusy(false);
            }
          }}
        >
          Simulate arrivals
        </button>
        {simulation && (
          <div className="simulation-result" role="status">
            <p>
              {simulation.placements.length} placements available ·{" "}
              {simulation.unplaced} cannot be placed.
            </p>
            <p>
              Based on saved capacity. Staged transfers and clinical
              suitability are not included.
            </p>
            {rooms.map((r) => {
              const n = simulation.placements.filter(
                (p) => p.roomId === r.id,
              ).length;
              return n ? (
                <div key={r.id}>
                  Room {r.roomNumber}
                  <strong> +{n}</strong>
                </div>
              ) : null;
            })}
          </div>
        )}
      </details>
    </aside>
  );
}
