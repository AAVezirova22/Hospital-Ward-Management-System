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
import type { PatientDirectoryItem, PatientDirectoryPage } from "../../api/contracts";
import { Plus, Search, ArrowUpRight } from "../../icons";
import { EntityForm } from "../administration/EntityForm";
import { DataTable } from "../../components/data-table/DataTable";
import { useUrlState } from "../../components/useUrlState";
import { LoadingState } from "../../components/LoadingState";
export function Patients() {
  const user = useUser();
  const [search, setSearch] = useUrlState("q");
  const [pageSelection, setPageSelection] = useState({ search: "", page: 0 });
  const page = pageSelection.search === search ? pageSelection.page : 0;
  const [edit, setEdit] = useState<Row | null>(null);
  const { data, error, isLoading } = useData(
    "/patients?q=" + encodeURIComponent(search) + `&page=${page}&size=20`,
  );
  const directory = data as PatientDirectoryPage | undefined;
  return (
    <>
      <Title
        eyebrow="Patient directory"
        title="People at the center."
        description="Search patient records, manage details and open an operational history."
      >
        {user.role !== "DOCTOR" && (
          <button className="primary" onClick={() => setEdit({})}>
            <Plus size={18} />
            New patient
          </button>
        )}
      </Title>
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
