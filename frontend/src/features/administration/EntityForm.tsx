"use client";
import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, fullName, type Row } from "../../api";
import { useAllPages, ErrorBox, Modal } from "../../components/workspace";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Check } from "../../icons";
type Field = {
  key: string;
  label: string;
  type?: string;
  required?: boolean;
  placeholder?: string;
  options?: { value: string; label: string }[];
};
export type EntityConfig = {
  title: string;
  singular: string;
  description: string;
  fields: Field[];
};
export const configs: Record<string, EntityConfig> = {
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
      {
        key: "phoneNumber",
        label: "Phone number (E.164)",
        placeholder: "+359888123456",
      },
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
};
export function EntityForm({
  kind,
  record,
  onClose,
  config,
}: {
  kind: string;
  record: Row;
  onClose: () => void;
  config?: EntityConfig;
}) {
  const cfg = config ?? configs[kind],
    client = useQueryClient();
  const [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  const { data: doctors } = useAllPages<Row>("/doctors?active=true");
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
            : f.key === "phoneNumber"
              ? z
                  .string()
                  .regex(
                    /^$|^\+[1-9]\d{7,14}$/,
                    "Use E.164, for example +359888123456",
                  )
                  .optional()
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
                <option value="">Select...</option>
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
                placeholder={f.placeholder}
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
            {busy ? "Saving..." : "Save " + cfg.singular}
            <Check size={17} />
          </button>
        </div>
      </form>
    </Modal>
  );
}
