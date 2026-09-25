import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  api,
  allPages,
  fullName,
  activeDepartment,
  type User,
} from "../../api";

import type {
  AdmissionView,
  RoomCapacity,
  ArrivalPlan,
} from "../../api/contracts";

import { missingCapabilities } from "../../room-capabilities";
import {
  executableOrder,
  validateTransfer,
  projectRooms,
  type PlannedTransfer,
} from "./model";

export function useWardPlanner(user: User) {
  const client = useQueryClient();
  const roomsQuery = useQuery({
    queryKey: ["/rooms", activeDepartment()],
    queryFn: () => allPages<RoomCapacity>("/rooms"),
    refetchInterval: 15000,
  });
  const admissionsQuery = useQuery({
    queryKey: ["/admissions?status=ACTIVE", activeDepartment()],
    queryFn: () => allPages<AdmissionView>("/admissions?status=ACTIVE"),
    refetchInterval: 15000,
  });
  const rooms = roomsQuery.data ?? [],
    admissions = admissionsQuery.data ?? [];
  const [plan, setPlan] = useState<PlannedTransfer[]>([]),
    [selected, setSelected] = useState<number>(),
    [destination, setDestination] = useState("");
  const [bedIdentifier, setBedIdentifier] = useState("");
  const [review, setReview] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [arrivals, setArrivals] = useState(0),
    [simulation, setSimulation] = useState<ArrivalPlan>(),
    [changedRoom, setChangedRoom] = useState<number>();
  const [dischargeDate, setDischargeDate] = useState("");
  const canWrite = user.role !== "DOCTOR";
  const active = admissions.filter((v) => v.admission.status === "ACTIVE");
  const current = active.find((v) => v.admission.id === selected);
  const roomName = (id: number) =>
    rooms.find((r) => r.id === id)?.roomNumber ?? "Unavailable";
  const stale = plan.some((p) => {
    const v = active.find((view) => view.admission.id === p.admissionId);
    const destination = rooms.find((room) => room.id === p.toRoomId);
    return (
      !v ||
      v.admission.version !== p.version ||
      v.assignment?.roomId !== p.fromRoomId ||
      destination?.version !== p.toRoomVersion ||
      !(destination?.bedIdentifiers ?? []).includes(p.bedIdentifier)
    );
  });
  const conflicts = projectRooms(rooms, plan).some(
    (r) =>
      r.projectedBeds > r.bedCount - (r.heldBeds ?? 0) ||
      r.projectedBeds < 0 ||
      (!r.active && plan.some((p) => p.toRoomId === r.id)) ||
      plan.some(
        (p) =>
          p.toRoomId === r.id &&
          missingCapabilities(p.requiredRoomCapabilities, r.capabilities).length > 0,
      ),
  );
  function stage(id: number, roomId: number) {
    if (!canWrite || busy) return;
    const view = active.find((v) => v.admission.id === id);
    const problem = validateTransfer(
      view,
      rooms.find((r) => r.id === roomId),
      rooms,
      plan,
    );
    if (problem || !view?.assignment) {
      setError(problem ?? "Admission unavailable.");
      return;
    }
    const target = rooms.find((room) => room.id === roomId)!;
    const takenBeds = new Set([
      ...active.flatMap((patient) => patient.rooms
        .filter((entry) => !entry.assignment.releasedAt && entry.assignment.roomId === roomId)
        .map((entry) => entry.assignment.bedIdentifier)),
      ...plan.filter((transfer) => transfer.admissionId !== id && transfer.toRoomId === roomId)
        .map((transfer) => transfer.bedIdentifier),
    ]);
    const preferredBed = roomId === Number(destination) ? bedIdentifier : "";
    const targetBed = preferredBed && !takenBeds.has(preferredBed)
      ? preferredBed
      : target.bedIdentifiers?.find((identifier) => !takenBeds.has(identifier));
    if (!targetBed) {
      setError("Choose an available named bed in the destination room.");
      return;
    }
    setPlan((p) => [
      ...p.filter((x) => x.admissionId !== id),
      {
        admissionId: id,
        patientName: fullName(view.patient),
        fromRoomId: view.assignment!.roomId,
        toRoomId: roomId,
        bedIdentifier: targetBed,
        toRoomVersion: target.version,
        requiredRoomCapabilities: view.admission.requiredRoomCapabilities ?? [],
        version: view.admission.version,
      },
    ]);
    setReview(false);
    setError("");
    setNotice("Transfer staged. Review and confirm to update records.");
    setSimulation(undefined);
  }
  return {
    client,
    roomsQuery,
    admissionsQuery,
    rooms,
    admissions,
    plan,
    setPlan,
    selected,
    setSelected,
    destination,
    setDestination,
    bedIdentifier,
    setBedIdentifier,
    review,
    setReview,
    busy,
    setBusy,
    error,
    setError,
    notice,
    setNotice,
    arrivals,
    setArrivals,
    simulation,
    setSimulation,
    changedRoom,
    setChangedRoom,
    dischargeDate,
    setDischargeDate,
    canWrite,
    active,
    current,
    roomName,
    stale,
    conflicts,
    stage,
  };
}
