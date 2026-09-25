"use client";
import React, { useState } from "react";
import { fullName, date, patientHref, type Row } from "../../api";
import {
  Link,
  useUser,
  useData,
  ErrorBox,
  Empty,
  Status,
  Title,
} from "../../components/workspace";
import { ArrowUpRight, ClipboardList } from "../../icons";
export function Admissions() {
  const user = useUser();
  const [status, setStatus] = useState("ACTIVE");
  const [search, setSearch] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [doctorId, setDoctorId] = useState("");
  const [sort, setSort] = useState({ key: "admissionDate", direction: "desc" as "asc" | "desc" });
  const [page, setPage] = useState(0);
  const { data: doctors } = useData("/doctors");
  const params = new URLSearchParams({ page: String(page), size: "20" });
  params.set("sort", sort.key);
  params.set("direction", sort.direction);
  if (status) params.set("status", status);
  if (search.trim()) params.set("q", search.trim());
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  if (doctorId) params.set("doctorId", doctorId);
  const { data, error, isLoading } = useData("/admissions?" + params.toString());
  const rows = Array.isArray(data?.items) ? data.items : [];
  const updateFilter = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(0);
  };
  const sortableHeader = (key: string, label: string) => {
    const active = sort.key === key;
    return (
      <th scope="col" aria-sort={active ? (sort.direction === "asc" ? "ascending" : "descending") : "none"}>
        <button
          className="table-sort"
          type="button"
          aria-label={`Sort by ${label}${active ? `, currently ${sort.direction === "asc" ? "ascending" : "descending"}` : ""}`}
          onClick={() => {
            setSort({ key, direction: active && sort.direction === "asc" ? "desc" : "asc" });
            setPage(0);
          }}
        >
          {label}{active ? (sort.direction === "asc" ? " ↑" : " ↓") : " ↕"}
        </button>
      </th>
    );
  };
  return (
    <>
      <Title
        eyebrow="Hospitalization"
        title="Every stay, connected."
        description="Follow admissions from placement through transfer and discharge."
      />
      <div className="toolbar">
        <label>
          Search admissions
          <input
            aria-label="Search admissions"
            placeholder="Patient name, identifier or admission number"
            value={search}
            onChange={(e) => updateFilter(setSearch)(e.target.value)}
          />
        </label>
      </div>
      <div className="report-filters admission-filters">
        <label>
          Status
          <select
            aria-label="Admission status"
            value={status}
            onChange={(e) => updateFilter(setStatus)(e.target.value)}
          >
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="DISCHARGED">Discharged</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </label>
        <label>
          Admitted from
          <input
            aria-label="Admitted from"
            type="date"
            value={from}
            onChange={(e) => updateFilter(setFrom)(e.target.value)}
          />
        </label>
        <label>
          Admitted through
          <input
            aria-label="Admitted through"
            type="date"
            value={to}
            onChange={(e) => updateFilter(setTo)(e.target.value)}
          />
        </label>
        {user.role !== "DOCTOR" && (
          <label>
            Attending doctor
            <select
              aria-label="Attending doctor"
              value={doctorId}
              onChange={(e) => updateFilter(setDoctorId)(e.target.value)}
            >
              <option value="">All doctors</option>
              {Array.isArray(doctors) &&
                doctors.map((doctor: Row) => (
                  <option value={doctor.id} key={doctor.id}>
                    {fullName(doctor)}
                  </option>
                ))}
            </select>
          </label>
        )}
        <button
          className="secondary"
          type="button"
          disabled={!status && !search && !from && !to && !doctorId}
          onClick={() => {
            setStatus("");
            setSearch("");
            setFrom("");
            setTo("");
            setDoctorId("");
            setPage(0);
          }}
        >
          Clear filters
        </button>
      </div>
      <ErrorBox error={error} />
      <div className="panel table-panel">
        <div className="toolbar">
          <ClipboardList size={18} />
          <span>Admission register · {data?.totalElements ?? 0} records</span>
        </div>
        <table>
          <thead>
            <tr>
              {sortableHeader("patient", "Patient")}
              {sortableHeader("admissionDate", "Admission")}
              <th scope="col">Attending doctor</th>
              {sortableHeader("room", "Room")}
              {sortableHeader("status", "Status")}
            </tr>
          </thead>
          <tbody>
            {rows.map((v: Row) => (
              <tr key={v.admission.id}>
                <td>
                  <Link to={patientHref(v.patient)}>
                    {fullName(v.patient)}
                    <ArrowUpRight size={14} />
                  </Link>
                </td>
                <td>
                  <span className="mono">{v.admission.admissionNumber}</span>
                  <small>{date(v.admission.admissionDateTime)}</small>
                </td>
                <td>Dr. {fullName(v.doctor)}</td>
                <td>
                  {v.rooms.find((r: Row) => !r.assignment.releasedAt)?.room
                    .roomNumber || "Not assigned"}
                </td>
                <td>
                  <Status value={v.admission.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {isLoading && <div className="skeleton">Loading admissions…</div>}
        {!isLoading && rows.length === 0 && <Empty />}
        <div className="table-pagination">
          <span>
            Page {(data?.page ?? page) + 1} of {Math.max(1, data?.totalPages ?? 0)}
          </span>
          <button
            className="secondary"
            disabled={(data?.page ?? page) === 0 || isLoading}
            onClick={() => setPage((data?.page ?? page) - 1)}
          >
            Previous
          </button>
          <button
            className="secondary"
            disabled={!data?.hasNext || isLoading}
            onClick={() => setPage(data?.nextPage ?? page + 1)}
          >
            Next
          </button>
        </div>
      </div>
    </>
  );
}
