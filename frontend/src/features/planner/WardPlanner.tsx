"use client";
import { RefreshCw, CheckCircle2 } from "lucide-react";
import { api, type User } from "../../api";
import { WardMap } from "./WardMap";
import { PlanReview } from "./PlanReview";
import { PlannerControls } from "./PlannerControls";
import { executableOrder } from "./model";
import { LoadingState } from "../../components/LoadingState";
import { useWardPlanner } from "./useWardPlanner";

export function WardPlanner({ user }: { user: User }) {
  const {
    client,
    roomsQuery,
    admissionsQuery,
    rooms,
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
  } = useWardPlanner(user);
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
          <span className="eyebrow">Ward planner</span>
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
        <PlannerControls
          canWrite={canWrite}
          busy={busy}
          loadError={Boolean(roomsQuery.error || admissionsQuery.error)}
          selected={selected}
          destination={destination}
          rooms={rooms}
          active={active}
          current={current}
          roomName={roomName}
          dischargeDate={dischargeDate}
          arrivals={arrivals}
          simulation={simulation}
          onSelect={(id, discharge) => {
            setSelected(id);
            setDischargeDate(discharge ?? "");
          }}
          onDestination={setDestination}
          onStage={stage}
          onDischargeDate={setDischargeDate}
          onSchedule={schedule}
          onArrivals={(value) => {
            setArrivals(value);
            setSimulation(undefined);
          }}
          onSimulation={setSimulation}
          onBusy={setBusy}
          onError={setError}
        />
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
            onVacant={
              canWrite && !busy
                ? (roomId) => {
                    setDestination(String(roomId));
                    if (selected) stage(selected, roomId);
                    else
                      setNotice(
                        "Choose a patient to preview a transfer to this room.",
                      );
                  }
                : undefined
            }
            changedRoom={changedRoom}
          />
          <PlanReview
            plan={plan}
            review={review}
            busy={busy}
            stale={stale}
            conflicts={conflicts}
            loadError={Boolean(roomsQuery.error || admissionsQuery.error)}
            roomName={roomName}
            onRemove={(admissionId) => {
              setPlan((list) =>
                list.filter((x) => x.admissionId !== admissionId),
              );
              setReview(false);
            }}
            onDiscard={() => {
              setPlan([]);
              setReview(false);
            }}
            onConfirm={() => (review ? confirm() : setReview(true))}
          />
        </div>
      </div>
    </>
  );
}
