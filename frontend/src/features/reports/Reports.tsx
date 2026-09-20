"use client";
import React, { useState, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter, usePathname } from "next/navigation";
import { api, fullName, money, date, patientHref, type Row, type User } from "../../api";
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
export function Reports() {
  const today = new Date().toISOString().slice(0, 10);
  const [from, setFrom] = useUrlState("from", today.slice(0, 8) + "01"),
    [to, setTo] = useUrlState("to", today),
    [patientId, setPatient] = useUrlState("patientId"),
    [doctorId, setDoctor] = useUrlState("doctorId"),
    [roomId, setRoom] = useUrlState("roomId"),
    [mode, setMode] = useUrlState("mode", "procedures");
  const { data: patients } = useData("/patients"),
    { data: doctors } = useData("/doctors"),
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
              Patient
              <select
                value={patientId}
                onChange={(e) => setPatient(e.target.value)}
              >
                <option value="">All permitted patients</option>
                {Array.isArray(patients) &&
                  patients.map((p: Row) => (
                    <option value={p.id} key={p.id}>
                      {fullName(p)}
                    </option>
                  ))}
              </select>
            </label>
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
      <ErrorBox error={error} />
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
