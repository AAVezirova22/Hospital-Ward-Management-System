import type { AdmissionView, RoomCapacity } from "../../api/contracts";
import { missingCapabilities } from "../../room-capabilities";
export interface PlannedTransfer {
  admissionId: number;
  patientName: string;
  fromRoomId: number;
  toRoomId: number;
  toRoomVersion: number;
  requiredRoomCapabilities: string[];
  version: number;
}
export function projectRooms(rooms: RoomCapacity[], plan: PlannedTransfer[]) {
  return rooms.map((room) => ({
    ...room,
    projectedBeds:
      room.occupiedBeds +
      plan.filter((p) => p.toRoomId === room.id).length -
      plan.filter((p) => p.fromRoomId === room.id).length,
  }));
}
export function validateTransfer(
  view: AdmissionView | undefined,
  destination: RoomCapacity | undefined,
  rooms: RoomCapacity[],
  plan: PlannedTransfer[],
) {
  if (!view || view.admission.status !== "ACTIVE" || !view.assignment)
    return "Choose an active admission.";
  if (!destination?.active) return "This room is inactive.";
  if (view.assignment.roomId === destination.id)
    return "The patient is already in this room.";
  const missing = missingCapabilities(
    view.admission.requiredRoomCapabilities,
    destination.capabilities,
  );
  if (missing.length)
    return `${destination.roomNumber} is missing required capabilities: ${missing.join(", ")}.`;
  const projected = projectRooms(
    rooms,
    plan.filter((p) => p.admissionId !== view.admission.id),
  );
  if (
    (projected.find((r) => r.id === destination.id)?.projectedBeds ??
      destination.bedCount) >= destination.bedCount
  )
    return "The destination has no capacity in this plan.";
  return null;
}
// Sequential confirmations cannot execute a full-room swap without an empty staging bed.
export function executableOrder(
  rooms: RoomCapacity[],
  plan: PlannedTransfer[],
): PlannedTransfer[] | null {
  const counts = new Map(rooms.map((r) => [r.id, r.occupiedBeds]));
  const pending = [...plan],
    ordered: PlannedTransfer[] = [];
  while (pending.length) {
    const index = pending.findIndex((p) => {
      const r = rooms.find((r) => r.id === p.toRoomId);
      return (
        r?.active &&
        missingCapabilities(p.requiredRoomCapabilities, r.capabilities).length === 0 &&
        (counts.get(r.id) ?? r.bedCount) < r.bedCount
      );
    });
    if (index < 0) return null;
    const [next] = pending.splice(index, 1);
    counts.set(next.fromRoomId, (counts.get(next.fromRoomId) ?? 0) - 1);
    counts.set(next.toRoomId, (counts.get(next.toRoomId) ?? 0) + 1);
    ordered.push(next);
  }
  return ordered;
}
