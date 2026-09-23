"use client";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../../api";
import type { RoomCapacity } from "../../api/contracts";
import { WardMap } from "../planner/WardMap";

const events = [
  { title: "Synthetic patient arrived", room: 0, delta: 1 },
  { title: "Second arrival placed", room: 1, delta: 1 },
  { title: "Blood panel recorded", room: 0, delta: 0 },
  { title: "Discharge confirmed", room: 0, delta: -1 },
  { title: "Transfer prepared for review", room: 1, delta: 0 },
  { title: "Transfer confirmed · SIM-2 → SIM-1", room: 1, delta: -1 },
];
export function HospitalSimulation() {
  const status = useQuery({
    queryKey: ["demo-status"],
    queryFn: () => api<{ enabled: boolean }>("/demo/status"),
  });
  const [step, setStep] = useState(0),
    [running, setRunning] = useState(false),
    [started, setStarted] = useState(false);
  useEffect(() => {
    if (!running) return;
    const timer = setInterval(
      () => setStep((n) => Math.min(events.length, n + 1)),
      5000,
    );
    return () => clearInterval(timer);
  }, [running]);
  useEffect(() => {
    if (step === events.length) setRunning(false);
  }, [step]);
  if (!status.data?.enabled) return null;
  const rooms = [0, 1].map((i) => {
      const occupied =
      1 +
      events
        .slice(0, step)
        .filter((e) => e.room === i)
          .reduce((n, e) => n + e.delta, 0) + (step === events.length && i === 0 ? 1 : 0);
    return {
      id: i + 1,
      roomNumber: `SIM-${i + 1}`,
      bedCount: 2,
      active: true,
      occupiedBeds: occupied,
      heldBeds: 0,
      activeHeldBeds: 0,
      availableBeds: 2 - occupied,
      holds: [],
      version: 0,
      createdAt: "",
      updatedAt: "",
    } satisfies RoomCapacity;
  });
  return (
    <section className="panel hospital-simulation">
      <div className="section-heading">
        <div>
          <h2>Hospital simulation</h2>
          <p>Synthetic evening rehearsal · no patient records are changed</p>
        </div>
        <button
          className="secondary"
          onClick={() => {
            setStarted(true);
            setStep(0);
            setRunning(true);
          }}
          disabled={running}
        >
          Run busy evening simulation
        </button>
      </div>
      {started && (
        <>
          <div className="simulation-toolbar">
            <strong>
              {step === events.length
                ? "Simulation complete"
                : running
                  ? "Rehearsal running"
                  : "Rehearsal paused"}
            </strong>
            <span>{step * 5} / 30 seconds</span>
            {step < events.length && (
              <button
                className="secondary"
                onClick={() => setRunning(!running)}
              >
                {running ? "Pause" : "Resume"}
              </button>
            )}
            <button
              className="text-button"
              onClick={() => {
                setRunning(false);
                setStarted(false);
                setStep(0);
              }}
            >
              Close simulation
            </button>
          </div>
          <WardMap rooms={rooms} />
          <div role="status">
            {rooms
              .filter((r) => r.availableBeds === 0)
              .map((r) => (
                <p className="capacity-alert" key={r.id}>
                  Simulation alert: {r.roomNumber} at full capacity
                </p>
              ))}
          </div>
          <ol className="simulation-events" aria-live="polite">
            {events.slice(0, step).map((e, i) => (
              <li key={e.title}>
                <time>00:{String((i + 1) * 5).padStart(2, "0")}</time>
                {e.title}
                <small>Room SIM-{e.room + 1}</small>
              </li>
            ))}
          </ol>
        </>
      )}
    </section>
  );
}
