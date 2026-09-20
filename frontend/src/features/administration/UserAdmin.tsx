"use client";
import { useState } from "react";
import { type Row } from "../../api";
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
import { EntityForm } from "./EntityForm";

export function UserAdmin() {
  const { data, error, isLoading } = useData("/users");
  const [edit, setEdit] = useState<Row | null>(null);
  const [search, setSearch] = useUrlState("q");
  const user = useUser();
  return (
    <>
      <Title
        eyebrow="Team access"
        title="Access with accountability."
        description="Manage staff accounts, physician links and role-based permissions."
      >
        {user.role === "ADMIN" && (
          <button className="primary" onClick={() => setEdit({})}>
            <Plus size={18} />
            Add user
          </button>
        )}
      </Title>
      <ErrorBox error={error} />
      <section className="panel table-panel">
        <label className="toolbar">
          Search users
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search records"
          />
        </label>
        <table>
          <thead>
            <tr>
              {["Username", "Role", "Doctor ID", "Status"].map((c) => (
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
                  [r.username, r.email]
                    .filter(Boolean)
                    .join(" ")
                    .toLowerCase()
                    .includes(search.toLowerCase()),
                )
                .map((r: Row) => (
                  <tr key={r.id}>
                    <td>
                      {r.username}
                      {r.email && (
                        <small className="muted">
                          {String(r.email)} ·{" "}
                          {r.emailVerified
                            ? "Email verified"
                            : "Awaiting verification"}
                        </small>
                      )}
                      {r.requestedRole === "DOCTOR" && r.role !== "DOCTOR" && (
                        <small className="status">
                          Doctor access requested
                        </small>
                      )}
                    </td>
                    <td>{String(r.role).replaceAll("_", " ")}</td>
                    <td>{(r.doctorId as number) || "Not assigned"}</td>
                    <td>
                      <Status value={r.enabled ? "ACTIVE" : "DISABLED"} />
                    </td>
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
        <EntityForm kind="users" record={edit} onClose={() => setEdit(null)} />
      )}
    </>
  );
}
