"use client";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import Link from "next/link";
import { api, date, activeDepartment } from "../../api";
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
    queryKey: ["/reports/operations", activeDepartment()],
    queryFn: () => api<OperationsReport>("/reports/operations"),
    refetchInterval: 15000,
  });
  const roomQuery = useQuery({
    queryKey: ["/rooms", activeDepartment()],
    queryFn: () => api<RoomCapacity[]>("/rooms"),
    refetchInterval: 15000,
  });
  const admissionQuery = useQuery({
    queryKey: ["/admissions", activeDepartment()],
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
  const today = d.trends.at(-1),
    yesterday = d.trends.at(-2);
  const admissionChange =
    today && yesterday ? today.admissions - yesterday.admissions : null;
  const censusChange =
    today && yesterday ? today.occupied - yesterday.occupied : null;
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
            {d.scope === "Department" && censusChange !== null
              ? `${censusChange > 0 ? "+" : ""}${censusChange} patients vs yesterday’s closing census`
              : "Department-wide capacity"}
          </small>
          <Sparkline
            values={d.trends.slice(-7).map((t) => t.occupied)}
            label={`Seven-day census (${d.scope})`}
          />
        </article>
        <article className="operation-stat">
          <span>Admissions today</span>
          <strong>{today?.admissions ?? 0}</strong>
          <p>
            {admissionChange === null
              ? "Comparison unavailable"
              : `${admissionChange > 0 ? "+" : ""}${admissionChange} vs yesterday`}
          </p>
          <small>{d.scope} · UTC</small>
          <Sparkline
            values={d.trends.slice(-7).map((t) => t.admissions)}
            label="Seven-day admissions"
          />
        </article>
        <article className="operation-stat">
          <span>Average active stay</span>
          <strong>
            {d.averageStayDays.toFixed(1)}
            <small> days</small>
          </strong>
          <p>{d.scope}</p>
          <small>
            {d.longStayPatients} stays over {d.thresholds.longStayDays} days
          </small>
        </article>
        <article className="operation-stat">
          <span>Expected discharges today</span>
          <strong>{d.expectedDischargesToday}</strong>
          <p>Staff-scheduled dates · UTC</p>
          <small>Scheduled dates, not a discharge forecast</small>
        </article>
      </div>
      <div className="section-heading">
        <div>
          <h2>The ward, at a glance.</h2>
          <p>Room capacity · live updates with a 15-second refresh fallback</p>
        </div>
        {!presentation && (
          <Link className="secondary" href="/app/planner">
            Open ward planner
          </Link>
        )}
      </div>
      <div className="compact-capacity">
        <strong>
          {occupied} / {beds} beds occupied
        </strong>
        <meter
          min={0}
          max={Math.max(1, beds)}
          value={occupied}
          aria-label="Department occupied beds"
        />
        <Link href="/app/planner">Open interactive ward map →</Link>
      </div>
      <div className="overview-floor-plan">
        <WardMap
          rooms={rooms}
          admissions={admissions}
          selected={selectedAdmission}
          onSelect={presentation ? undefined : setSelectedAdmission}
          warning={d.thresholds.warningPercent}
          critical={d.thresholds.criticalPercent}
        />
      </div>
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
        <section className="panel activity-panel">
          <details open>
            <summary>Live activity</summary>
            <ol className="operational-feed">
              {d.activity.slice(0, presentation ? 5 : 8).map((e) => {
                const v = admissions.find(
                  (v) => v.admission.id === e.admissionId,
                );
                return (
                  <li key={e.id}>
                    <span>
                      {e.eventType.toLowerCase().replaceAll("_", " ")}
                    </span>
                    <strong>
                      {v ? (
                        <Link href={`/app/patients/${v.patient.id}`}>
                          {v.patient.firstName} {v.patient.lastName} →
                        </Link>
                      ) : (
                        "Admission unavailable"
                      )}
                    </strong>
                    <span className="muted">
                      {v ? v.admission.admissionNumber : ""}
                    </span>
                    <small>{date(e.timestamp)}</small>
                  </li>
                );
              })}
            </ol>
            {!d.activity.length && <p>No activity in your scope yet.</p>}
          </details>
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
