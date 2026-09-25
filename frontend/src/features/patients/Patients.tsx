"use client";
import React, { useState } from "react";
import { fullName, patientHref, type Row } from "../../api";
import {
  Link,
  useUser,
  useData,
  ErrorBox,
  Empty,
  Title,
} from "../../components/workspace";
import type {
  PatientDirectoryItem,
  PatientDirectoryPage,
} from "../../api/contracts";
import { Plus, Search, ArrowUpRight } from "../../icons";
import { EntityForm } from "../administration/EntityForm";
import { DataTable } from "../../components/data-table/DataTable";
import { useUrlState } from "../../components/useUrlState";
import { LoadingState } from "../../components/LoadingState";
import { PatientDocumentDraft } from "./PatientDocumentDraft";
export function Patients() {
  const user = useUser();
  const [search, setSearch] = useUrlState("q");
  const [activeAdmission, setActiveAdmission] = useUrlState("activeAdmission");
  const [doctorId, setDoctor] = useUrlState("doctorId");
  const [roomId, setRoom] = useUrlState("roomId");
  const [pageSelection, setPageSelection] = useState({ search: "", page: 0 });
  const page = pageSelection.search === search ? pageSelection.page : 0;
  const [edit, setEdit] = useState<Row | null>(null);
  const [documentDraft, setDocumentDraft] = useState(false);

  const params = new URLSearchParams();
  if (search) params.set("q", search);
  if (activeAdmission) params.set("activeAdmission", activeAdmission);
  if (doctorId) params.set("doctorId", doctorId);
  if (roomId) params.set("roomId", roomId);
  params.set("page", String(page));
  params.set("size", "20");

  const { data, error, isLoading } = useData("/patients?" + params.toString());
  const directory = data as PatientDirectoryPage | undefined;
  const { data: doctors } = useData("/doctors");
  const { data: rooms } = useData("/rooms");
  return (
    <>
      <Title
        eyebrow="Patient directory"
        title="People at the center."
        description="Search patient records, manage details and open an operational history."
      >
        <button className="secondary" onClick={() => setDocumentDraft(true)}>
          Review document
        </button>
        {user.role !== "DOCTOR" && (
          <button className="primary" onClick={() => setEdit({})}>
            <Plus size={18} />
            New patient
          </button>
        )}
      </Title>
      {documentDraft && (
        <PatientDocumentDraft onClose={() => setDocumentDraft(false)} />
      )}
      <div className="toolbar">
        <Search size={18} />
        <input
          aria-label="Search patients"
          placeholder="Search by name or patient ID"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
          }}
        />
        <span>{directory?.totalElements ?? 0} records</span>
      </div>
      <div className="report-filters patient-directory-filters">
        <label>
          Admission status
          <select
            aria-label="Admission status"
            value={activeAdmission}
            onChange={(e) => setActiveAdmission(e.target.value)}
          >
            <option value="">Any admission status</option>
            <option value="true">Currently admitted</option>
            <option value="false">Not currently admitted</option>
          </select>
        </label>
        {user.role !== "DOCTOR" && (
          <label>
            Assigned doctor
            <select
              aria-label="Assigned doctor"
              value={doctorId}
              onChange={(e) => setDoctor(e.target.value)}
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
        <label>
          Current room
          <select
            aria-label="Current room"
            value={roomId}
            onChange={(e) => setRoom(e.target.value)}
          >
            <option value="">All rooms</option>
            {Array.isArray(rooms) &&
              rooms.map((room: Row) => (
                <option value={room.id} key={room.id}>
                  {room.roomNumber}
                </option>
              ))}
          </select>
        </label>
      </div>
      <ErrorBox error={error} />
      <div className="panel table-panel">
        <DataTable<PatientDirectoryItem>
          rows={directory?.items ?? []}
          serverPagination={{
            page: directory?.page ?? page,
            totalPages: directory?.totalPages ?? 0,
            totalElements: directory?.totalElements ?? 0,
            onPageChange: (nextPage) =>
              setPageSelection({ search, page: nextPage }),
          }}
          rowKey={(p) => p.id}
          columns={[
            {
              key: "name",
              label: "Patient",
              value: fullName,
              render: (p) => (
                <Link className="person-link" to={patientHref(p)}>
                  <span className="avatar">
                    {p.firstName[0]}
                    {p.lastName[0]}
                  </span>
                  {fullName(p)}
                </Link>
              ),
            },
            {
              key: "identifier",
              label: "Patient ID",
              value: (p) => p.patientIdentifier,
              render: (p) => p.patientIdentifier,
            },
            {
              key: "birth",
              label: "Date of birth",
              value: (p) => p.dateOfBirth,
              render: (p) => p.dateOfBirth,
            },
            {
              key: "open",
              label: "Record",
              render: (p) => (
                <Link to={patientHref(p)} className="text-button">
                  Open dossier
                  <ArrowUpRight size={16} />
                </Link>
              ),
            },
          ]}
        />
        {isLoading ? (
          <LoadingState label="Loading patients" />
        ) : (
          directory?.items.length === 0 && <Empty />
        )}
      </div>
      {edit && (
        <EntityForm
          kind="patients"
          record={edit}
          onClose={() => setEdit(null)}
        />
      )}
    </>
  );
}
