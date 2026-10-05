"use client";
import React, { useState } from "react";
import { fullName, money, type Row } from "../../api";
import {
  useUser,
  useData,
  ErrorBox,
  Empty,
  Status,
  Title,
} from "../../components/workspace";
import { Plus, ArrowUpRight, Search } from "../../icons";
import { useUrlState } from "../../components/useUrlState";
import { EntityForm, configs } from "./EntityForm";
import { Appointments } from "./Appointments";
export function Catalogue({ kind }: { kind: string }) {
  const cfg = configs[kind];
  const [edit, setEdit] = useState<Row | null>(null);
  const [view, setView] = useUrlState("view", "directory");
  const [doctorId, setDoctorId] = useUrlState("doctorId");
  const [search, setSearch] = useUrlState("q");
  const [pageText, setPageText] = useUrlState("page", "0");
  const page = Math.max(0, Number.parseInt(pageText, 10) || 0);
  const params = new URLSearchParams({ q: search, page: String(page) });
  const { data, error, isLoading } = useData(`/${kind}?${params}`);
  const user = useUser();
  const columns =
    kind === "doctors"
      ? ["Doctor", "Specialty", "Status"]
      : ["Procedure", "Code", "Current cost", "Status"];
  return (
    <>
      <Title
        eyebrow="Department directory"
        title={cfg.title}
        description={cfg.description}
      >
        {user.role === "ADMIN" && (
          <button className="primary" onClick={() => setEdit({})}>
            <Plus size={18} />
            Add {cfg.singular}
          </button>
        )}
      </Title>
      <ErrorBox error={error} />
      {kind === "doctors" && (
        <nav className="appointment-tabs" aria-label="Doctors views">
          <button
            className={view !== "appointments" ? "primary" : "secondary"}
            aria-pressed={view !== "appointments"}
            onClick={() => setView("directory")}
          >
            Directory
          </button>
          <button
            className={view === "appointments" ? "primary" : "secondary"}
            aria-pressed={view === "appointments"}
            onClick={() => setView("appointments")}
          >
            Appointments
          </button>
        </nav>
      )}
      {kind === "doctors" && view === "appointments" ? (
        <Appointments doctorId={doctorId} onDoctorChange={setDoctorId} />
      ) : (
        <section className="panel table-panel">
          <div className="toolbar">
            <Search size={18} />
            <input
              aria-label={`Search ${kind}`}
              value={search}
              onChange={(e) => {
                setPageText("0");
                setSearch(e.target.value);
              }}
              placeholder={`Search ${kind}`}
            />
            <span>{data?.totalElements ?? 0} records</span>
          </div>
          <table>
            <thead>
              <tr>
                {columns.map((c) => (
                  <th scope="col" key={c}>
                    {c}
                  </th>
                ))}
                {(user.role === "ADMIN" || kind === "doctors") && (
                  <th scope="col">Actions</th>
                )}
              </tr>
            </thead>
            <tbody>
              {Array.isArray(data?.items) &&
                data.items.map((r: Row) => (
                  <tr key={r.id}>
                    {kind === "doctors" ? (
                      <>
                        <td>
                          <strong>Dr. {fullName(r)}</strong>
                          <small>{r.doctorIdentifier}</small>
                        </td>
                        <td>{r.specialty}</td>
                        <td>
                          <Status value={r.active ? "ACTIVE" : "INACTIVE"} />
                        </td>
                      </>
                    ) : (
                      <>
                        <td>
                          <strong>{r.procedureName}</strong>
                        </td>
                        <td className="mono">{r.procedureCode}</td>
                        <td>{money(r.currentCost)}</td>
                        <td>
                          <Status value={r.active ? "ACTIVE" : "INACTIVE"} />
                        </td>
                      </>
                    )}
                    {(user.role === "ADMIN" || kind === "doctors") && (
                      <td>
                        {kind === "doctors" &&
                          (user.role !== "DOCTOR" ||
                            user.doctorId === r.id) && (
                            <button
                              className="text-button"
                              onClick={() => {
                                setDoctorId(String(r.id));
                                setView("appointments");
                              }}
                            >
                              Appointments
                              <ArrowUpRight size={15} />
                            </button>
                          )}
                        {user.role === "ADMIN" && (
                          <button
                            className="text-button"
                            onClick={() => setEdit(r)}
                          >
                            Edit
                            <ArrowUpRight size={15} />
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
            </tbody>
          </table>
          {isLoading ? (
            <div className="skeleton">Loading records…</div>
          ) : (
            Array.isArray(data?.items) && !data.items.length && <Empty />
          )}
          {data && data.totalPages > 0 && (
            <div
              className="toolbar"
              role="navigation"
              aria-label={`${cfg.title} pages`}
            >
              <button
                className="secondary"
                disabled={data.page === 0}
                onClick={() => setPageText(String(data.page - 1))}
              >
                Previous
              </button>
              <span>
                Page {data.page + 1} of {data.totalPages} · {data.totalElements}{" "}
                records
              </span>
              <button
                className="secondary"
                disabled={!data.hasNext}
                onClick={() => setPageText(String(data.nextPage))}
              >
                Next
              </button>
            </div>
          )}
        </section>
      )}
      {edit && (
        <EntityForm kind={kind} record={edit} onClose={() => setEdit(null)} />
      )}
    </>
  );
}
