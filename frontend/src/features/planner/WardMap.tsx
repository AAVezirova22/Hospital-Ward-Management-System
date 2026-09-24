"use client";
import { BedDouble, ArrowRight, Activity } from "../../icons";
import { Fragment } from "react";
import type { AdmissionView, RoomCapacity } from "../../api/contracts";
import { projectRooms, type PlannedTransfer } from "./model";
import { missingCapabilities } from "../../room-capabilities";

export function WardMap({
  rooms,
  admissions = [],
  plan = [],
  selected,
  onSelect,
  onDrop,
  changedRoom,
  warning = 75,
  critical = 90,
  onVacant,
  requiredRoomCapabilities = [],
  currentRoomId,
}: {
  rooms: RoomCapacity[];
  admissions?: AdmissionView[];
  plan?: PlannedTransfer[];
  selected?: number;
  onSelect?: (id: number) => void;
  onDrop?: (admissionId: number, roomId: number) => void;
  changedRoom?: number;
  warning?: number;
  critical?: number;
  onVacant?: (roomId: number) => void;
  requiredRoomCapabilities?: string[];
  currentRoomId?: number;
}) {
  return (
    <div className="ward-map" aria-label="Live ward capacity map">
      {projectRooms(rooms, plan)
        .sort((a, b) =>
          a.roomNumber.localeCompare(b.roomNumber, undefined, {
            numeric: true,
          }),
        )
        .map((room, index) => {
          const heldBeds = room.heldBeds ?? 0;
          const percent = ((room.projectedBeds + heldBeds) / room.bedCount) * 100;
          const state = !room.active
            ? "inactive"
            : percent >= critical
              ? "critical"
              : percent >= warning
                ? "warning"
                : "safe";
          const missing = missingCapabilities(
            requiredRoomCapabilities,
            room.capabilities,
          );
          const alreadyAssigned = room.id === currentRoomId;
          const placementBlocked = missing.length > 0 || alreadyAssigned;
          const exclusionReason = [
            alreadyAssigned ? "already assigned to this room" : "",
            missing.length > 0 ? `missing ${missing.join(", ")}` : "",
          ].filter(Boolean).join("; ");
          const occupants = admissions.filter(
            (a) =>
              a.admission.status === "ACTIVE" &&
              (plan.find((p) => p.admissionId === a.admission.id)?.toRoomId ??
                a.assignment?.roomId) === room.id,
          );
          return (
            <Fragment key={room.id}>
              {index === Math.ceil(rooms.length / 2) && (
                <div className="ward-corridor">
                  <Activity size={18} />
                  <strong>Nurses’ station</strong>
                  <span>Central corridor · schematic layout</span>
                </div>
              )}
              <section
                className={`ward-room ${state} ${changedRoom === room.id ? "room-changed" : ""}`}
                onDragOver={
                  onDrop && room.active && !placementBlocked
                    ? (e) => {
                        e.preventDefault();
                        e.dataTransfer.dropEffect = "move";
                      }
                    : undefined
                }
                onDrop={
                  onDrop && room.active && !placementBlocked
                    ? (e) => {
                        e.preventDefault();
                        const id = Number(
                          e.dataTransfer.getData(
                            "application/medcore-admission",
                          ),
                        );
                        if (id) onDrop(id, room.id);
                      }
                    : undefined
                }
              >
                <div className="ward-room-heading">
                  <h3>Room {room.roomNumber}</h3>
                  <span>
                    {room.active ? `${Math.round(percent)}%` : "Inactive"}
                  </span>
                </div>
                <p>
                  {room.projectedBeds} / {room.bedCount} occupied{" "}
                  {room.projectedBeds !== room.occupiedBeds && (
                    <span className="projection">· projected</span>
                  )}
                </p>
                      {placementBlocked && (
                        <p className="room-requirement-warning">
                          Excluded for this patient: {exclusionReason}.
                        </p>
                      )}

                      {heldBeds > 0 && (
                        <p className="muted">
                          {heldBeds} bed{heldBeds === 1 ? "" : "s"} reserved for maintenance
                        </p>
                      )}
                {occupants.map((v) => (
                  <button
                    type="button"
                    className={`ward-patient ${selected === v.admission.id ? "selected" : ""}`}
                    key={v.admission.id}
                    disabled={!onSelect}
                    draggable={Boolean(onDrop)}
                    onClick={() => onSelect?.(v.admission.id)}
                    onDragStart={(e) => {
                      e.dataTransfer.setData(
                        "application/medcore-admission",
                        String(v.admission.id),
                      );
                      e.dataTransfer.effectAllowed = "move";
                      onSelect?.(v.admission.id);
                    }}
                  >
                    <BedDouble size={18} aria-hidden="true" />
                    {v.patient.firstName} {v.patient.lastName}
                    {plan.some((p) => p.admissionId === v.admission.id) && (
                      <ArrowRight size={14} />
                    )}
                  </button>
                ))}
                {Array.from(
                  {
                    length: Math.max(0, room.projectedBeds - occupants.length),
                  },
                  (_, i) => (
                    <div
                      className="ward-patient restricted"
                      key={`occupied-${i}`}
                    >
                      <BedDouble size={18} />
                      Occupied · outside your scope
                    </div>
                  ),
                )}
                {Array.from(
                  { length: Math.max(0, room.bedCount - room.projectedBeds - heldBeds) },
                  (_, i) => (
                    <button
                      type="button"
                      className="ward-patient vacant"
                      key={`free-${i}`}
                      disabled={!room.active || !onVacant || placementBlocked}
                      aria-label={
                        placementBlocked
                          ? `Room ${room.roomNumber} cannot accept this patient; ${exclusionReason}.`
                          : `Available bed in room ${room.roomNumber}`
                      }
                      onClick={() => onVacant?.(room.id)}
                    >
                      <BedDouble size={18} aria-hidden="true" />
                      {placementBlocked
                        ? "Not suitable"
                        : room.active
                          ? "Available"
                          : "Inactive"}
                      <small>
                        {placementBlocked ? exclusionReason : "Capacity slot"}
                      </small>
                    </button>
                  ),
                )}
                {(room.holds ?? []).map((hold) => (
                  <div className="ward-patient restricted" key={`hold-${hold.id}`}>
                    <BedDouble size={18} />
                    <span>
                      {hold.reason}
                      <small>
                        {new Date(hold.startsAt).toLocaleString()} - {new Date(hold.endsAt).toLocaleString()}
                      </small>
                    </span>
                  </div>
                ))}
              </section>
            </Fragment>
          );
        })}
      {!rooms.length && <p className="empty">No rooms have been configured.</p>}
    </div>
  );
}
