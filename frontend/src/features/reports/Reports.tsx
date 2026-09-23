"use client";
import React, { useState, useEffect, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter, usePathname } from "next/navigation";
import {
  api,
  activeDepartment,
  downloadFile,
  fullName,
  money,
  date,
  patientHref,
  type Row,
  type User,
} from "../../api";
import {
  Link,
  useData,
  useAllPages,
  ErrorBox,
  Empty,
  Title,
} from "../../components/workspace";
import { Download } from "../../icons";
import { useUrlState } from "../../components/useUrlState";
import { ProcedureCharts, CapacityChart } from "./ReportCharts";
import type {
  DoctorWorkloadReport,
  Patient,
  PatientDirectoryPage,
  WorkspaceList,
} from "../../api/contracts";
import { dateInTimeZone } from "../../date-time";

export function Reports() {
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<unknown>(null);

  const workspaces = useQuery<WorkspaceList>({
    queryKey: ["/workspaces", activeDepartment()],
    queryFn: () => api("/workspaces"),
  });

  const timeZone = workspaces.data?.timeZone ?? "UTC";
  const today = dateInTimeZone(new Date(), timeZone);
  const [from, setFrom] = useUrlState("from", today.slice(0, 8) + "01"),
    [to, setTo] = useUrlState("to", today),
    [patientId, setPatient] = useUrlState("patientId"),
    [doctorId, setDoctor] = useUrlState("doctorId"),
    [roomId, setRoom] = useUrlState("roomId"),
    [mode, setMode] = useUrlState("mode", "procedures");
  const [patientSearch, setPatientSearch] = useState("");
  const [patientPage, setPatientPage] = useState(0);

  const patientDirectoryQuery = useData(
    `/patients?q=${encodeURIComponent(patientSearch)}&page=${patientPage}&size=20`,
  );

  const patients = patientDirectoryQuery.data as
    PatientDirectoryPage | undefined;

  const selectedPatient = patients?.items.find(
    (p) => String(p.id) === patientId,
  );

  const selectedPatientQuery = useQuery({
    queryKey: ["/patients", patientId, activeDepartment()],
    queryFn: () =>
      api<{ patient: Patient }>(`/patients/${encodeURIComponent(patientId)}`),
    enabled: Boolean(patientId) && !selectedPatient,
  });

  const selectedPatientRecord =
    selectedPatient ?? selectedPatientQuery.data?.patient;

  const { data: doctors } = useAllPages<Row>("/doctors"),
    { data: rooms } = useAllPages<Row>("/rooms");
  const params = new URLSearchParams({
    from,
    to,
    ...(patientId ? { patientId } : {}),
    ...(doctorId ? { doctorId } : {}),
  });
  const path =
    mode === "procedures"
      ? "/reports/procedures?" + params
      : mode === "capacity"
        ? "/reports/capacity"
        : mode === "doctor-workload"
          ? "/reports/doctor-workload?" + new URLSearchParams({ from, to })
          : "/reports/census?" +
            new URLSearchParams({
              ...(doctorId ? { doctorId } : {}),
              ...(roomId ? { roomId } : {}),
            });
  const { data, error, isLoading } = useData(path);
  const exportCsv = async () => {
    const department = activeDepartment();
    if (!department) {
      setExportError(new Error("Choose a department before exporting."));
      return;
    }
    setExporting(true);
    setExportError(null);
    try {
      const { blob, filename } = await downloadFile(
        "/reports/procedures.csv?" + params,
        department,
      );
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      link.hidden = true;
      document.body.append(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      setExportError(e);
    } finally {
      setExporting(false);
    }
  };
  return (
    <>
      <Title
        eyebrow="Operational reporting"
        title="Decisions, grounded in data."
        description="Authoritative reports calculated from saved department records."
      />
      <div className="tabs" role="group" aria-label="Report type">
        {["procedures", "census", "capacity", "doctor-workload"].map((m) => (
          <button
            className={mode === m ? "selected" : ""}
            aria-pressed={mode === m}
            onClick={() => setMode(m)}
            key={m}
          >
            {m === "census"
              ? "Hospitalized patients"
              : m === "capacity"
                ? "Bed capacity"
                : m === "doctor-workload"
                  ? "Doctor workload"
                  : "Procedure activity"}
          </button>
        ))}
      </div>
      <div className="report-filters">
        <small className="muted">Calendar dates use {timeZone}.</small>
        {(mode === "procedures" || mode === "doctor-workload") && (
          <>
            <label>
              From
              <input
                type="date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </label>
            <label>
              To
              <input
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </label>
            {mode === "procedures" && (
              <>
                <label>
                  Find patient
                  <input
                    aria-label="Search patients for reports"
                    value={patientSearch}
                    onChange={(e) => {
                      setPatientSearch(e.target.value);
                      setPatientPage(0);
                    }}
                    placeholder="Name or patient ID"
                  />
                </label>
                <label>
                  Patient
                  <select
                    value={patientId}
                    onChange={(e) => setPatient(e.target.value)}
                  >
                    <option value="">All permitted patients</option>
                    {patientId && !selectedPatient && (
                      <option value={patientId}>
                        {selectedPatientRecord
                          ? fullName(selectedPatientRecord)
                          : selectedPatientQuery.error
                            ? "Selected patient unavailable"
                            : "Loading selected patient…"}
                      </option>
                    )}
                    {patients?.items.map((p) => (
                      <option value={p.id} key={p.id}>
                        {fullName(p)}
                      </option>
                    ))}
                  </select>
                </label>
                {patients && patients.totalPages > 1 && (
                  <div className="table-pagination">
                    <span>
                      Page {patients.page + 1} of {patients.totalPages}
                    </span>
                    <button
                      className="secondary"
                      disabled={patients.page === 0}
                      onClick={() => setPatientPage(patients.page - 1)}
                    >
                      Previous
                    </button>
                    <button
                      className="secondary"
                      disabled={!patients.hasNext}
                      onClick={() =>
                        setPatientPage(patients.nextPage ?? patients.page + 1)
                      }
                    >
                      Next
                    </button>
                  </div>
                )}
              </>
            )}
          </>
        )}
        {(mode === "procedures" || mode === "census") && (
          <label>
            Doctor
            <select
              value={doctorId}
              onChange={(e) => setDoctor(e.target.value)}
            >
              <option value="">All doctors</option>
              {Array.isArray(doctors) &&
                doctors.map((d: Row) => (
                  <option value={d.id} key={d.id}>
                    {fullName(d)}
                  </option>
                ))}
            </select>
          </label>
        )}
        {mode === "census" && (
          <label>
            Room
            <select value={roomId} onChange={(e) => setRoom(e.target.value)}>
              <option value="">All rooms</option>
              {Array.isArray(rooms) &&
                rooms.map((r: Row) => (
                  <option value={r.id} key={r.id}>
                    {r.roomNumber}
                  </option>
                ))}
            </select>
          </label>
        )}
        {mode === "procedures" && (
          <button
            type="button"
            className="secondary"
            onClick={() => void exportCsv()}
            disabled={exporting}
            aria-busy={exporting}
          >
            <Download size={16} />
            {exporting ? "Preparing CSV…" : "Export CSV"}
          </button>
        )}
      </div>
      <ErrorBox
        error={
          error ||
          (mode === "procedures"
            ? patientDirectoryQuery.error || selectedPatientQuery.error
            : null)
        }
      />
      <ErrorBox error={exportError} />
      {isLoading ? (
        <div className="skeleton" role="status" aria-live="polite">
          Calculating report…
        </div>
      ) : (
        data && (
          <>
            <div>
              {mode === "procedures" && (
                <ProcedureCharts
                  report={data as import("../../api/contracts").ProcedureReport}
                  onDoctor={setDoctor}
                />
              )}{" "}
              {mode === "capacity" && (
                <CapacityChart
                  rooms={data as import("../../api/contracts").RoomCapacity[]}
                  onRoom={(id) => {
                    setRoom(id);
                    setMode("census");
                  }}
                />
              )}
            </div>
            <section className="panel table-panel">
              {mode === "procedures" ? (
                <>
                  <div className="report-total">
                    <span>{data.rows?.length || 0} performed procedures</span>
                    <strong>{money(data.totalCost)}</strong>
                  </div>
                  <table>
                    <thead>
                      <tr>
                        <th scope="col">Patient</th>
                        <th scope="col">Procedure</th>
                        <th scope="col">Doctor</th>
                        <th scope="col">Performed at</th>
                        <th scope="col">Recorded cost</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.rows?.map((v: Row) => (
                        <tr key={v.record.id}>
                          <td>{fullName(v.patient)}</td>
                          <td>{v.procedure.procedureName}</td>
                          <td>{fullName(v.doctor)}</td>
                          <td>{date(v.record.performedAt, timeZone)}</td>
                          <td>{money(v.record.priceAtExecution)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {!data.rows?.length && (
                    <Empty text="No procedures in this period." />
                  )}
                  <div className="report-groups">
                    {Object.entries(data.byDoctor || {}).map(([id, total]) => (
                      <span key={id}>
                        {Array.isArray(doctors) &&
                          fullName(
                            doctors.find((d: Row) => String(d.id) === id),
                          )}
                        <strong>{money(Number(total))}</strong>
                      </span>
                    ))}
                  </div>
                </>
              ) : mode === "census" ? (
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Patient</th>
                      <th scope="col">Doctor</th>
                      <th scope="col">Room</th>
                      <th scope="col">Admitted</th>
                    </tr>
                  </thead>
                  <tbody>
                    {Array.isArray(data) &&
                      data.map((v: Row) => (
                        <tr key={v.admission.id}>
                          <td>
                            <Link to={patientHref(v.patient)}>
                              {fullName(v.patient)}
                            </Link>
                          </td>
                          <td>{fullName(v.doctor)}</td>
                          <td>
                            {
                              v.rooms.find((r: Row) => !r.assignment.releasedAt)
                                ?.room.roomNumber
                            }
                          </td>
                          <td>{date(v.admission.admissionDateTime)}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              ) : mode === "doctor-workload" ? (
                <>
                  <h2>Doctor workload</h2>
                  <p className="muted">
                    Scope: {(data as DoctorWorkloadReport).scope}. Recent
                    procedures include {(data as DoctorWorkloadReport).from}{" "}
                    through {(data as DoctorWorkloadReport).to} (
                    {(data as DoctorWorkloadReport).timeZone}).
                  </p>
                  <div className="report-total">
                    <strong>
                      {(data as DoctorWorkloadReport).rows.length} active doctor
                      {(data as DoctorWorkloadReport).rows.length === 1
                        ? ""
                        : "s"}
                    </strong>
                  </div>
                  <table>
                    <thead>
                      <tr>
                        <th scope="col">Doctor</th>
                        <th scope="col">Specialty</th>
                        <th scope="col">Active admissions</th>
                        <th scope="col">Assigned beds</th>
                        <th scope="col">Recent procedures</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(data as DoctorWorkloadReport).rows.map((row) => (
                        <tr key={row.doctor.id}>
                          <th scope="row">{fullName(row.doctor)}</th>
                          <td>{row.doctor.specialty || "Not recorded"}</td>
                          <td>{row.activeAdmissions}</td>
                          <td>{row.assignedBeds}</td>
                          <td>{row.recentProcedures}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {(data as DoctorWorkloadReport).rows.length === 0 && (
                    <Empty text="No active doctors are available in this reporting scope." />
                  )}
                </>
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Room</th>
                      <th scope="col">Total beds</th>
                      <th scope="col">Occupied</th>
                      <th scope="col">Held</th>
                      <th scope="col">Available</th>
                      <th scope="col">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {Array.isArray(data) &&
                      data.map((r: Row) => (
                        <tr key={r.id}>
                          <td>{r.roomNumber}</td>
                          <td>{r.bedCount}</td>
                          <td>{r.occupiedBeds}</td>
                          <td>{r.heldBeds}</td>
                          <td>{r.availableBeds}</td>
                          <td>{r.active ? "Active" : "Inactive"}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              )}
            </section>
          </>
        )
      )}
    </>
  );
}
