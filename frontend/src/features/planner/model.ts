import type { AdmissionView, RoomCapacity } from "../../api/contracts";
export interface PlannedTransfer {
  admissionId: number;
  patientName: string;
  fromRoomId: number;
  toRoomId: number;
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
function allocatableBeds(room: RoomCapacity) {
  return room.bedCount - (room.heldBeds ?? 0);
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
  const projected = projectRooms(
    rooms,
    plan.filter((p) => p.admissionId !== view.admission.id),
  );
  if (
    (projected.find((r) => r.id === destination.id)?.projectedBeds ??
      allocatableBeds(destination)) >= allocatableBeds(destination)
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
      return r?.active && (counts.get(r.id) ?? r.bedCount) < allocatableBeds(r);
    });
    if (index < 0) return null;
    const [next] = pending.splice(index, 1);
    counts.set(next.fromRoomId, (counts.get(next.fromRoomId) ?? 0) - 1);
    counts.set(next.toRoomId, (counts.get(next.toRoomId) ?? 0) + 1);
    ordered.push(next);
  }
  return ordered;
}
