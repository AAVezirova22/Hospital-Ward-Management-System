"use client";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, RefreshCw, CheckCircle2 } from "lucide-react";
import { api, fullName, type User } from "../../api";
import type {
  AdmissionView,
  RoomCapacity,
  ArrivalPlan,
} from "../../api/contracts";
import { WardMap } from "./WardMap";
import {
  executableOrder,
  validateTransfer,
  projectRooms,
  type PlannedTransfer,
} from "./model";
import { LoadingState } from "../../components/LoadingState";

export function WardPlanner({ user }: { user: User }) {
  const client = useQueryClient();
  const roomsQuery = useQuery({
    queryKey: ["/rooms"],
    queryFn: () => api<RoomCapacity[]>("/rooms"),
    refetchInterval: 15000,
  });
  const admissionsQuery = useQuery({
    queryKey: ["/admissions"],
    queryFn: () => api<AdmissionView[]>("/admissions"),
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
    const v = active.find((v) => v.admission.id === p.admissionId);
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
  async function confirm() {
    if (busy || stale || conflicts) return;
    const order = executableOrder(rooms, plan);
    if (!order) {
      setError(
        "This plan needs an available staging room before it can be applied.",
      );
      return;
    }
    setBusy(true);
    setError("");
    let completed = 0;
    try {
      for (const p of order) {
        await api(`/admissions/${p.admissionId}/transfer`, "POST", {
          roomId: p.toRoomId,
          version: p.version,
          reason: "Confirmed ward plan",
        });
        completed++;
        setChangedRoom(p.toRoomId);
        setPlan((list) => list.filter((x) => x.admissionId !== p.admissionId));
      }
      setReview(false);
      setNotice(
        `${completed} transfer${completed === 1 ? "" : "s"} completed. Audit history and capacity updated.`,
      );
    } catch (e) {
      setError(
        `${completed} transfers completed. Remaining transfers were not applied: ${(e as Error).message} Refresh and review the remaining plan.`,
      );
    } finally {
      await client.invalidateQueries();
      setBusy(false);
    }
  }
  async function schedule() {
    if (!current || !canWrite || busy) return;
    setBusy(true);
    setError("");
    try {
      await api(
        `/admissions/${current.admission.id}/expected-discharge`,
        "POST",
        { date: dischargeDate || null, version: current.admission.version },
      );
      await client.invalidateQueries();
      setNotice(
        "Expected discharge updated. This does not discharge the patient.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (roomsQuery.isLoading || admissionsQuery.isLoading)
    return <LoadingState label="Loading ward plan" />;
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">WARD PLANNER</span>
          <h1>Make room for what’s next.</h1>
          <p>Explore placements, review the impact, then confirm your plan.</p>
        </div>
        <button
          className="secondary"
          disabled={busy}
          onClick={() => client.invalidateQueries()}
        >
          <RefreshCw size={16} />
          Refresh
        </button>
      </div>
      {(error || roomsQuery.error || admissionsQuery.error) && (
        <p className="error" role="alert">
          {error || roomsQuery.error?.message || admissionsQuery.error?.message}
        </p>
      )}
      {notice && (
        <p className="planner-notice" role="status">
          <CheckCircle2 size={18} />
          {notice}
        </p>
      )}
      <div className="planner-layout">
        <aside className="panel planner-controls">
          <h2>{canWrite ? "Plan a placement" : "Ward overview"}</h2>
          <p>Capacity slots represent occupancy, not assigned bed numbers.</p>
          {canWrite ? (
            <>
              <label>
                Patient
                <select
                  aria-label="Patient"
                  value={selected ?? ""}
                  disabled={busy}
                  onChange={(e) => {
                    setSelected(Number(e.target.value) || undefined);
                    setDischargeDate(
                      active.find(
                        (a) => a.admission.id === Number(e.target.value),
                      )?.admission.expectedDischargeDate ?? "",
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
                  onChange={(e) => setDestination(e.target.value)}
                >
                  <option value="">Choose a room</option>
                  {rooms.map((r) => (
                    <option key={r.id} value={r.id} disabled={!r.active}>
                      Room {r.roomNumber} ·{" "}
                      {r.active ? `${r.availableBeds} free now` : "Inactive"}
                    </option>
                  ))}
                </select>
              </label>
              <button
                className="primary"
                disabled={
                  !selected ||
                  !destination ||
                  busy ||
                  Boolean(roomsQuery.error || admissionsQuery.error)
                }
                onClick={() => stage(selected!, Number(destination))}
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
                    Expected discharge (UTC)
                    <input
                      type="date"
                      min={new Date().toISOString().slice(0, 10)}
                      value={dischargeDate}
                      onChange={(e) => setDischargeDate(e.target.value)}
                    />
                  </label>
                  <p>
                    Currently:{" "}
                    {current.admission.expectedDischargeDate ?? "Not scheduled"}
                  </p>
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={schedule}
                  >
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
                onChange={(e) => {
                  setArrivals(Number(e.target.value));
                  setSimulation(undefined);
                }}
              />
            </label>
            <button
              className="secondary"
              disabled={
                busy ||
                !Number.isInteger(arrivals) ||
                arrivals < 0 ||
                arrivals > 100
              }
              onClick={async () => {
                setBusy(true);
                setError("");
                try {
                  setSimulation(
                    await api<ArrivalPlan>(
                      `/planner/simulate?arrivals=${arrivals}`,
                    ),
                  );
                } catch (e) {
                  setError((e as Error).message);
                } finally {
                  setBusy(false);
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
        <div>
          <div className="ward-legend">
            <span>Live capacity</span>
            <span>{rooms.filter((r) => r.active).length} active rooms</span>
            <span>
              {plan.length ? "Simulation in progress" : "No unsaved transfers"}
            </span>
          </div>
          <WardMap
            rooms={rooms}
            admissions={active}
            plan={plan}
            selected={selected}
            onSelect={canWrite && !busy ? setSelected : undefined}
            onDrop={canWrite && !busy ? stage : undefined}
            onVacant={canWrite && !busy ? (roomId) => {
              setDestination(String(roomId));
              if (selected) stage(selected, roomId);
              else setNotice("Choose a patient to preview a transfer to this room.");
            } : undefined}
            changedRoom={changedRoom}
          />
          {plan.length > 0 && (
            <section className="panel plan-review">
              <h2>{review ? "Review your plan" : "Staged transfers"}</h2>
              <p>
                The attending doctor stays unchanged. Each successful transfer
                creates a ROOM_TRANSFERRED audit event.
              </p>
              {stale && (
                <p className="error" role="alert">
                  A staged admission changed. Remove it and prepare a new
                  transfer.
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
                    onClick={() => {
                      setPlan((list) =>
                        list.filter((x) => x.admissionId !== p.admissionId),
                      );
                      setReview(false);
                    }}
                  >
                    Remove
                  </button>
                </div>
              ))}
              {review && (
                <p>
                  Transfers apply one at a time. If capacity or permissions
                  change, processing stops; completed transfers remain saved.
                </p>
              )}
              <div className="actions">
                <button
                  className="secondary"
                  disabled={busy}
                  onClick={() => {
                    setPlan([]);
                    setReview(false);
                  }}
                >
                  Discard plan
                </button>
                <button
                  className="primary"
                  disabled={
                    busy ||
                    stale ||
                    conflicts ||
                    Boolean(roomsQuery.error || admissionsQuery.error)
                  }
                  onClick={() => (review ? confirm() : setReview(true))}
                >
                  {busy
                    ? "Applying plan…"
                    : review
                      ? "Confirm transfers"
                      : "Review plan"}
                </button>
              </div>
            </section>
          )}
        </div>
      </div>
    </>
  );
}
