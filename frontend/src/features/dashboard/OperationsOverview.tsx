"use client";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import Link from "next/link";
import { api, date } from "../../api";
import type {
  OperationsReport,
  RoomCapacity,
  AdmissionView,
} from "../../api/contracts";
import { WardMap } from "../planner/WardMap";
import { BedDrawer } from "../planner/BedDrawer";
import { LoadingState } from "../../components/LoadingState";

export function Sparkline({
  values,
  label,
}: {
  values: number[];
  label: string;
}) {
  const max = Math.max(1, ...values),
    width = 280,
    height = 64;
  const points = values
    .map(
      (v, i) =>
        `${(i * width) / Math.max(1, values.length - 1)},${height - (v / max) * (height - 8)}`,
    )
    .join(" ");
  return (
    <svg
      className="sparkline"
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label={`${label}: ${values.join(", ")}`}
    >
      <polyline
        points={points}
        fill="none"
        stroke="currentColor"
        strokeWidth="2.5"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
export function OperationsOverview({
  presentation = false,
}: {
  presentation?: boolean;
}) {
  const [selectedDay, setSelectedDay] = useState("");
  const [selectedAdmission, setSelectedAdmission] = useState<number>();
  const ops = useQuery({
    queryKey: ["/reports/operations"],
    queryFn: () => api<OperationsReport>("/reports/operations"),
    refetchInterval: 15000,
  });
  const roomQuery = useQuery({
    queryKey: ["/rooms"],
    queryFn: () => api<RoomCapacity[]>("/rooms"),
    refetchInterval: 15000,
  });
  const admissionQuery = useQuery({
    queryKey: ["/admissions"],
    queryFn: () => api<AdmissionView[]>("/admissions"),
    refetchInterval: 15000,
  });
  if (ops.isLoading || roomQuery.isLoading) return <LoadingState />;
  if (ops.error || roomQuery.error || admissionQuery.error)
    return (
      <div className="error" role="alert">
        {ops.error?.message ||
          roomQuery.error?.message ||
          admissionQuery.error?.message}
        <button
          onClick={() => {
            ops.refetch();
            roomQuery.refetch();
            admissionQuery.refetch();
          }}
        >
          Retry
        </button>
      </div>
    );
  if (!ops.data || !roomQuery.data) return null;
  const d = ops.data,
    rooms = roomQuery.data,
    admissions = admissionQuery.data ?? [];
  const activeRooms = rooms.filter((r) => r.active),
    beds = activeRooms.reduce((n, r) => n + r.bedCount, 0),
    occupied = activeRooms.reduce((n, r) => n + r.occupiedBeds, 0),
    percent = beds ? (occupied / beds) * 100 : 0;
  const severity =
    percent >= d.thresholds.criticalPercent
      ? "critical"
      : percent >= d.thresholds.warningPercent
        ? "warning"
        : "safe";
  return (
    <div className="operations-overview">
      <div className="operations-metrics">
        <article className={`operation-stat ${severity}`}>
          <span>Department occupancy</span>
          <strong>
            {percent.toFixed(1)}
            <small>%</small>
          </strong>
          <p>
            {occupied} occupied · {beds - occupied} available
          </p>
          <small>
            Warning ≥ {d.thresholds.warningPercent}% · Critical ≥{" "}
            {d.thresholds.criticalPercent}%
          </small>
        </article>
        <article className="operation-stat">
          <span>Average active stay</span>
          <strong>
            {d.averageStayDays.toFixed(1)}
            <small> days</small>
          </strong>
          <p>{d.scope}</p>
          <Sparkline
            values={d.trends.slice(-7).map((t) => t.occupied)}
            label="Seven-day occupied census"
          />
        </article>
        <article className="operation-stat">
          <span>Expected discharges today</span>
          <strong>{d.expectedDischargesToday}</strong>
          <p>Staff-scheduled dates · UTC</p>
          <Sparkline
            values={d.trends.slice(-7).map((t) => t.admissions)}
            label="Seven-day admissions"
          />
        </article>
      </div>
      <div className="section-heading">
        <div>
          <h2>The ward, at a glance.</h2>
          <p>Live room capacity · refreshed every 15 seconds</p>
        </div>
        {!presentation && (
          <Link className="secondary" href="/app/planner">
            Open ward planner
          </Link>
        )}
      </div>
      <WardMap
        rooms={rooms}
        admissions={admissions}
        selected={selectedAdmission}
        onSelect={presentation ? undefined : setSelectedAdmission}
        warning={d.thresholds.warningPercent}
        critical={d.thresholds.criticalPercent}
      />
      {selectedAdmission &&
        admissions.find((v) => v.admission.id === selectedAdmission) && (
          <BedDrawer
            admission={
              admissions.find((v) => v.admission.id === selectedAdmission)!
            }
            onClose={() => setSelectedAdmission(undefined)}
          />
        )}
      <div className="operations-bottom">
        <section className="panel">
          <h2>Needs attention</h2>
          <div className="attention-list">
            {activeRooms
              .filter(
                (r) =>
                  (r.occupiedBeds / r.bedCount) * 100 >=
                  d.thresholds.warningPercent,
              )
              .map((r) => (
                <div key={r.id}>
                  <strong>Room {r.roomNumber}</strong>
                  <span>
                    {r.availableBeds} beds available · {r.occupiedBeds}/
                    {r.bedCount} occupied
                  </span>
                </div>
              ))}
            {rooms
              .filter((r) => !r.active)
              .map((r) => (
                <div key={r.id}>
                  <strong>Room {r.roomNumber} is inactive</strong>
                  <span>Excluded from available capacity</span>
                </div>
              ))}
            <div>
              <strong>{d.longStayPatients} longer stays</strong>
              <span>
                Active admissions over {d.thresholds.longStayDays} days.
                Operational review only.
              </span>
            </div>
          </div>
        </section>
        <section className="panel">
          <h2>Recent operational activity</h2>
          <ol className="operational-feed">
            {d.activity.slice(0, presentation ? 5 : 8).map((e) => {
              const v = admissions.find(
                (v) => v.admission.id === e.admissionId,
              );
              return (
                <li key={e.id}>
                  <span>{e.eventType.toLowerCase().replaceAll("_", " ")}</span>
                  <strong>
                    {v
                      ? `${v.patient.firstName} ${v.patient.lastName}`
                      : "Admission"}
                  </strong>
                  <small>{date(e.timestamp)}</small>
                </li>
              );
            })}
          </ol>
          {!d.activity.length && <p>No activity in your scope yet.</p>}
        </section>
      </div>
      {!presentation && (
        <section className="panel">
          <h2>Admissions and discharges</h2>
          <p>{d.scope} · select a day to inspect the census</p>
          <div className="daily-chart">
            {d.trends.map((t) => (
              <button
                key={t.date}
                className={selectedDay === t.date ? "selected" : ""}
                onClick={() => setSelectedDay(t.date)}
                aria-label={`${t.date}: ${t.admissions} admissions, ${t.discharges} discharges`}
              >
                <span className="day-bars">
                  <i style={{ height: `${8 + t.admissions * 16}px` }} />
                  <i style={{ height: `${8 + t.discharges * 16}px` }} />
                </span>
                <small>{t.date.slice(5)}</small>
              </button>
            ))}
          </div>
          <p className="chart-legend">Mint: admissions · Gold: discharges</p>
          {selectedDay && (
            <p role="status">
              {selectedDay}:{" "}
              {d.trends.find((t) => t.date === selectedDay)?.occupied} occupied
              at day end (today: current census).
            </p>
          )}
        </section>
      )}
      <small className="muted">
        Updated {date(d.asOf)} · Historical census uses admission intervals, not
        a forecast.
      </small>
    </div>
  );
}
