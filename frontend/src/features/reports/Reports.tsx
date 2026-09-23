"use client";
import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, activeDepartment, fullName, money, date, patientHref, type Row } from "../../api";
import {
  Link,
  useUser,
  useData,
  ErrorBox,
  Empty,
  Status,
  Modal,
  Title,
} from "../../components/workspace";
import { Download } from "../../icons";
import { useUrlState } from "../../components/useUrlState";
import { ProcedureCharts, CapacityChart } from "./ReportCharts";
import type { Patient, PatientDirectoryPage } from "../../api/contracts";
export function Reports() {
  const today = new Date().toISOString().slice(0, 10);
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
  const patients = patientDirectoryQuery.data as PatientDirectoryPage | undefined;
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
  const { data: doctors } = useData("/doctors"),
    { data: rooms } = useData("/rooms");
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
        : "/reports/census?" +
          new URLSearchParams({
            ...(doctorId ? { doctorId } : {}),
            ...(roomId ? { roomId } : {}),
          });
  const { data, error, isLoading } = useData(path);
  return (
    <>
      <Title
        eyebrow="Operational reporting"
        title="Decisions, grounded in data."
        description="Authoritative reports calculated from saved department records."
      />
      <div className="tabs">
        {["procedures", "census", "capacity"].map((m) => (
          <button
            className={mode === m ? "selected" : ""}
            onClick={() => setMode(m)}
            key={m}
          >
            {m === "census"
              ? "Hospitalized patients"
              : m === "capacity"
                ? "Bed capacity"
                : "Procedure activity"}
          </button>
        ))}
      </div>
      <div className="report-filters">
        {mode === "procedures" && (
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
        {mode !== "capacity" && (
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
          <a
            className="secondary"
            href={"/api/v1/reports/procedures.csv?" + params}
            download
          >
            <Download size={16} />
            Export CSV
          </a>
        )}
      </div>
      <ErrorBox
        error={error || patientDirectoryQuery.error || selectedPatientQuery.error}
      />
      {isLoading ? (
        <div className="skeleton">Calculating report…</div>
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
                          <td>{date(v.record.performedAt)}</td>
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
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Room</th>
                      <th scope="col">Total beds</th>
                      <th scope="col">Occupied</th>
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
