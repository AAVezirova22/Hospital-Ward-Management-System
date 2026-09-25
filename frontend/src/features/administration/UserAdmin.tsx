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
  const { data, error, isLoading } = useData("/users");
  const [edit, setEdit] = useState<Row | null>(null);
  const [search, setSearch] = useUrlState("q");
  const [roleFilter, setRoleFilter] = useUrlState("role");
  const [enabledFilter, setEnabledFilter] = useUrlState("enabled");
  const [doctorRequests, setDoctorRequests] = useUrlState("doctorRequests");
  const user = useUser();
  const users = Array.isArray(data) ? (data as Row[]) : [];
  const searchMatches = users.filter((r) =>
    [r.username, r.email]
      .filter(Boolean)
      .join(" ")
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  const matchesEnabled = (r: Row) =>
    enabledFilter === "" || String(Boolean(r.enabled)) === enabledFilter;
  const matchesRole = (r: Row) => roleFilter === "" || r.role === roleFilter;
  const matchesDoctorRequests = (r: Row) =>
    doctorRequests !== "pending" ||
    (r.requestedRole === "DOCTOR" && r.role !== "DOCTOR");
  const roles = [...new Set(users.map((r) => String(r.role)).filter(Boolean))].sort();
  const filteredUsers = searchMatches.filter(
    (r) => matchesRole(r) && matchesEnabled(r) && matchesDoctorRequests(r),
  );
  const roleCount = (role: string) =>
    searchMatches.filter(
      (r) => r.role === role && matchesEnabled(r) && matchesDoctorRequests(r),
    ).length;
  const enabledCount = (enabled: boolean) =>
    searchMatches.filter(
      (r) =>
        Boolean(r.enabled) === enabled &&
        matchesRole(r) &&
        matchesDoctorRequests(r),
    ).length;
  const doctorRequestCount = searchMatches.filter(
    (r) =>
      r.requestedRole === "DOCTOR" &&
      r.role !== "DOCTOR" &&
      matchesRole(r) &&
      matchesEnabled(r),
  ).length;
  const hasFilters = Boolean(search || roleFilter || enabledFilter || doctorRequests);
  const clearFilters = () => {
    setSearch("");
    setRoleFilter("");
    setEnabledFilter("");
    setDoctorRequests("");
  };
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
            placeholder="Search usernames or emails"
          />
        </label>
        <div className="toolbar" role="group" aria-label="Team access filters">
          <label>
            Role
            <select
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
            >
              <option value="">
                All roles (
                {
                  searchMatches.filter(
                    (r) => matchesEnabled(r) && matchesDoctorRequests(r),
                  ).length
                }
                )
              </option>
              {roles.map((role) => (
                <option key={role} value={role}>
                  {role.replaceAll("_", " ")} ({roleCount(role)})
                </option>
              ))}
            </select>
          </label>
          <label>
            Account status
            <select
              value={enabledFilter}
              onChange={(e) => setEnabledFilter(e.target.value)}
            >
              <option value="">
                All states (
                {
                  searchMatches.filter(
                    (r) => matchesRole(r) && matchesDoctorRequests(r),
                  ).length
                }
                )
              </option>
              <option value="true">Enabled ({enabledCount(true)})</option>
              <option value="false">Disabled ({enabledCount(false)})</option>
            </select>
          </label>
          <label>
            Doctor requests
            <select
              value={doctorRequests}
              onChange={(e) => setDoctorRequests(e.target.value)}
            >
              <option value="">
                All requests (
                {
                  searchMatches.filter(
                    (r) => matchesRole(r) && matchesEnabled(r),
                  ).length
                }
                )
              </option>
              <option value="pending">Pending only ({doctorRequestCount})</option>
            </select>
          </label>
          <button
            type="button"
            className="text-button"
            onClick={clearFilters}
            disabled={!hasFilters}
          >
            Clear filters
          </button>
        </div>
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
            {filteredUsers.map((r: Row) => (
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
          Array.isArray(data) && !filteredUsers.length && (
            hasFilters ? <p role="status">No users match these filters.</p> : <Empty />
          )
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
