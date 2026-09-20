"use client";
import React, { useState, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter, usePathname } from "next/navigation";
import { api, fullName, money, date, type Row, type User } from "../../api";
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
import { Plus, Check, ArrowUpRight } from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useUrlState } from "../../components/useUrlState";
type Field = {
  key: string;
  label: string;
  type?: string;
  required?: boolean;
  options?: { value: string; label: string }[];
};
const configs: Record<
  string,
  { title: string; singular: string; description: string; fields: Field[] }
> = {
  patients: {
    title: "Patients",
    singular: "patient",
    description: "Patient demographics",
    fields: [
      { key: "patientIdentifier", label: "Patient ID", required: true },
      { key: "firstName", label: "First name", required: true },
      { key: "lastName", label: "Last name", required: true },
      {
        key: "dateOfBirth",
        label: "Date of birth",
        type: "date",
        required: true,
      },
      { key: "phoneNumber", label: "Phone number" },
      { key: "address", label: "Address" },
    ],
  },
  doctors: {
    title: "The department team.",
    singular: "doctor",
    description: "Maintain the physician directory and active clinical team.",
    fields: [
      { key: "doctorIdentifier", label: "Doctor ID", required: true },
      { key: "firstName", label: "First name", required: true },
      { key: "lastName", label: "Last name", required: true },
      { key: "specialty", label: "Specialty", required: true },
      { key: "active", label: "Active", type: "checkbox" },
    ],
  },
  rooms: {
    title: "Rooms",
    singular: "room",
    description: "Room configuration",
    fields: [
      { key: "roomNumber", label: "Room number", required: true },
      { key: "bedCount", label: "Bed count", type: "number", required: true },
      { key: "active", label: "Active", type: "checkbox" },
    ],
  },
  procedures: {
    title: "A consistent catalogue.",
    singular: "procedure",
    description:
      "Department procedures and current pricing. Recorded costs retain their original value.",
    fields: [
      { key: "procedureCode", label: "Procedure code", required: true },
      { key: "procedureName", label: "Procedure name", required: true },
      {
        key: "currentCost",
        label: "Cost (EUR)",
        type: "number",
        required: true,
      },
      { key: "active", label: "Active", type: "checkbox" },
    ],
  },
  users: {
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
  },
};
export function EntityForm({
  kind,
  record,
  onClose,
}: {
  kind: string;
  record: Row;
  onClose: () => void;
}) {
  const cfg = configs[kind],
    client = useQueryClient();
  const [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  const { data: doctors } = useData("/doctors");
  const defaults: Row = {
    active: true,
    enabled: true,
    role: "MEDICAL_STAFF",
    ...record,
    password: "",
  };
  const shape: Record<string, z.ZodTypeAny> = {};
  cfg.fields.forEach(
    (f) =>
      (shape[f.key] =
        f.type === "checkbox"
          ? z.boolean()
          : f.type === "number"
            ? f.key === "bedCount"
              ? z.coerce
                  .number()
                  .int("Use a whole number of beds")
                  .min(1)
                  .max(100)
              : z.coerce.number().min(0)
            : f.required
              ? z.string().trim().min(1, "Required")
              : z.string().optional()),
  );
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Row>({
    defaultValues: defaults,
    resolver: zodResolver(z.object(shape)),
  });
  return (
    <Modal
      title={`${record.id ? "Edit" : "New"} ${cfg.singular}`}
      onClose={onClose}
    >
      <form
        className="form-grid"
        onSubmit={handleSubmit(async (values) => {
          setBusy(true);
          setError(null);
          try {
            const body: Row = { ...values, version: record.version ?? null };
            if (kind === "users") {
              body.doctorId =
                body.role === "DOCTOR" && body.doctorId
                  ? Number(body.doctorId)
                  : null;
              if (record.id && !body.password) delete body.password;
            }
            await api(
              "/" + kind + (record.id ? "/" + record.id : ""),
              record.id ? "PUT" : "POST",
              body,
            );
            await client.invalidateQueries();
            onClose();
          } catch (e) {
            setError(e as Error);
          } finally {
            setBusy(false);
          }
        })}
      >
        {cfg.fields.map((f) => (
          <label
            key={f.key}
            className={f.type === "checkbox" ? "inline-check form-full" : ""}
          >
            {f.label}
            {f.type === "select" ? (
              <select {...register(f.key)}>
                <option value="">Select…</option>
                {(f.key === "doctorId"
                  ? Array.isArray(doctors)
                    ? doctors
                        .filter((d: Row) => d.active)
                        .map((d: Row) => ({
                          value: String(d.id),
                          label: fullName(d),
                        }))
                    : []
                  : f.options || []
                ).map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            ) : (
              <input
                type={f.type || "text"}
                {...register(f.key)}
                readOnly={
                  kind === "users" && f.key === "username" && Boolean(record.id)
                }
                autoComplete={f.type === "password" ? "new-password" : "off"}
                max={
                  f.type === "date"
                    ? new Date().toISOString().slice(0, 10)
                    : undefined
                }
                step={
                  f.type === "number"
                    ? f.key === "bedCount"
                      ? "1"
                      : "0.01"
                    : undefined
                }
              />
            )}{" "}
            {errors[f.key] && (
              <small className="invalid">
                {String(errors[f.key]?.message)}
              </small>
            )}
          </label>
        ))}
        <div className="form-full">
          <ErrorBox error={error} />
        </div>
        <div className="modal-actions form-full">
          <button type="button" className="secondary" onClick={onClose}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Saving…" : "Save " + cfg.singular}
            <Check size={17} />
          </button>
        </div>
      </form>
    </Modal>
  );
}
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
