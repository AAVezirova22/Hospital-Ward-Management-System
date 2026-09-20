"use client";
import { useState } from "react";
import type { Account } from "../../api/contracts";
import {
  useUser,
  useData,
  ErrorBox,
  Empty,
  Status,
  Title,
} from "../../components/workspace";
import { Plus, ArrowUpRight } from "../../icons";
import { useUrlState } from "../../components/useUrlState";
import { EntityForm, type EntityConfig } from "./EntityForm";

const userConfig: EntityConfig = {
  title: "Access with accountability.",
  singular: "user",
  description:
    "Manage staff accounts, physician links and role-based permissions.",
  fields: [
    { key: "username", label: "Username", required: true },
    { key: "password", label: "Password (12+ characters)", type: "password" },
    {
      key: "role",
      label: "Role",
      type: "select",
      required: true,
      options: [
        { value: "MEDICAL_STAFF", label: "Medical staff" },
        { value: "DOCTOR", label: "Doctor" },
        { value: "ADMIN", label: "Administrator" },
        { value: "PATIENT", label: "Patient (registered account)" },
      ],
    },
    { key: "doctorId", label: "Linked doctor", type: "select" },
    { key: "enabled", label: "Enabled", type: "checkbox" },
  ],
};

export function UserAdmin() {
  const { data, error, isLoading } = useData<Account[]>("/users");
  const [edit, setEdit] = useState<Partial<Account> | null>(null);
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
                .filter((r) =>
                  [r.username, r.email]
                    .filter(Boolean)
                    .join(" ")
                    .toLowerCase()
                    .includes(search.toLowerCase()),
                )
                .map((r) => (
                  <tr key={r.id}>
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
                      {r.requestedRole === "DOCTOR" && r.role !== "DOCTOR" && (
                        <small className="status">
                          Doctor access requested
                        </small>
                      )}
                    </td>
                    <td>{String(r.role).replaceAll("_", " ")}</td>
                    <td>{r.doctorId || "Not assigned"}</td>
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
        <EntityForm
          kind="users"
          record={edit}
          onClose={() => setEdit(null)}
          config={userConfig}
        />
      )}
    </>
  );
}
