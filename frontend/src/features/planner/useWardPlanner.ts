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
    return (
      !v ||
      v.admission.version !== p.version ||
      v.assignment?.roomId !== p.fromRoomId
    );
  });
  const conflicts = projectRooms(rooms, plan).some(
    (r) =>
      r.projectedBeds > r.bedCount ||
      r.projectedBeds < 0 ||
      (!r.active && plan.some((p) => p.toRoomId === r.id)),
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
    setPlan((p) => [
      ...p.filter((x) => x.admissionId !== id),
      {
        admissionId: id,
        patientName: fullName(view.patient),
        fromRoomId: view.assignment!.roomId,
        toRoomId: roomId,
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
