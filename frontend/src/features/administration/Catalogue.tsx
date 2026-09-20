"use client";
import React, { useState } from "react";
import { api, fullName, money, type Row } from "../../api";
import {
  useUser,
  useData,
  ErrorBox,
  Empty,
  Status,
  Title,
} from "../../components/workspace";
import { Plus, ArrowUpRight } from "lucide-react";
import { useUrlState } from "../../components/useUrlState";
import { EntityForm, configs } from "./EntityForm";
export function Catalogue({ kind }: { kind: string }) {
  const cfg = configs[kind],
    { data, error, isLoading } = useData("/" + kind);
  const [edit, setEdit] = useState<Row | null>(null);
  const [search, setSearch] = useUrlState("q");
  const user = useUser();
  const columns =
    kind === "doctors"
      ? ["Doctor", "Specialty", "Status"]
      : kind === "procedures"
        ? ["Procedure", "Code", "Current cost", "Status"]
        : ["Username", "Role", "Doctor ID", "Status"];
  return (
    <>
      <Title
        eyebrow={kind === "users" ? "TEAM ACCESS" : "DEPARTMENT DIRECTORY"}
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
      <section className="panel table-panel">
        <label className="toolbar">
          Search {kind}
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search records"
          />
        </label>
        <table>
          <thead>
            <tr>
              {columns.map((c) => (
                <th scope="col" key={c}>
                  {c}
                </th>
              ))}
              {user.role === "ADMIN" && <th />}
            </tr>
          </thead>
          <tbody>
            {Array.isArray(data) &&
              data
                .filter((r: Row) =>
                  [
                    fullName(r),
                    r.username,
                    r.email,
                    r.specialty,
                    r.procedureName,
                    r.procedureCode,
                  ]
                    .filter(Boolean)
                    .join(" ")
                    .toLowerCase()
                    .includes(search.toLowerCase()),
                )
                .map((r: Row) => (
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
                    ) : kind === "procedures" ? (
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
                    ) : (
                      <>
                        <td>
                          {r.username}
                          {r.email && (
                            <small className="muted">
                              {r.email} ·{" "}
                              {r.emailVerified
                                ? "Email verified"
                                : "Awaiting verification"}
                            </small>
                          )}
                          {r.requestedRole === "DOCTOR" &&
                            r.role !== "DOCTOR" && (
                              <small className="status">
                                Doctor access requested
                              </small>
                            )}
                        </td>
                        <td>{r.role.replaceAll("_", " ")}</td>
                        <td>{r.doctorId || "Not assigned"}</td>
                        <td>
                          <Status value={r.enabled ? "ACTIVE" : "DISABLED"} />
                        </td>
                      </>
                    )}
                    {user.role === "ADMIN" && (
                      <td>
                        <button
                          className="text-button"
                          onClick={() => setEdit(r)}
                        >
                          Edit
                          <ArrowUpRight size={15} />
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
          </tbody>
        </table>
        {isLoading ? (
          <div className="skeleton">Loading records…</div>
        ) : (
          Array.isArray(data) && !data.length && <Empty />
        )}
      </section>
      {edit && (
        <EntityForm kind={kind} record={edit} onClose={() => setEdit(null)} />
      )}
    </>
  );
}
