"use client";
import React, { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, fullName, money } from "../../api";
import type {
  AdmissionView,
  Doctor,
  MedicalProcedure,
  Patient,
  RoomCapacity,
} from "../../api/contracts";
import { useUser, useData, ErrorBox, Modal } from "../../components/workspace";
import { CheckCircle2, ArrowRight } from "../../icons";
import { defaultProcedureTime } from "../../workflow-time";
/** Every field of the workflow form is edited as text and converted when it is submitted. */
type WorkflowValues = {
  doctorId: string;
  roomId: string;
  medicalProcedureId: string;
  reason: string;
  note: string;
  performedAt: string;
};

const TITLES: Record<string, string> = {
  admit: "Admit patient",
  transfer: "Transfer patient",
  discharge: "Discharge patient",
  procedure: "Record procedure",
  doctor: "Assign attending doctor",
};

const CONFIRMATIONS: Record<string, string> = {
  admit: "admission",
  transfer: "transfer",
  discharge: "discharge",
  procedure: "procedure",
  doctor: "assignment",
};

export function Workflow({
  kind,
  patient,
  active,
  onClose,
}: {
  kind: string;
  patient: Patient;
  active?: AdmissionView;
  onClose: () => void;
}) {
  const user = useUser(),
    client = useQueryClient();
  const { data: doctors } = useData<Doctor[]>("/doctors"),
    { data: rooms } = useData<RoomCapacity[]>("/rooms"),
    { data: procedures } = useData<MedicalProcedure[]>("/procedures");
  const [values, setValues] = useState<WorkflowValues>({
      doctorId: String(active?.doctor.id ?? user.doctorId ?? ""),
      roomId: "",
      medicalProcedureId: "",
      reason: "",
      note: "",
      performedAt: defaultProcedureTime(active?.admission.admissionDateTime),
    }),
    [review, setReview] = useState(kind === "discharge"),
    [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  const name = TITLES[kind];
  const update =
    (key: string) =>
    (
      e: React.ChangeEvent<
        HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
      >,
    ) =>
      setValues((v: Row) => ({ ...v, [key]: e.target.value }));
  const submit = async () => {
    setError(null);
    setBusy(true);
    try {
      const admission = active?.admission;
      if (kind === "admit")
        await api("/admissions", "POST", {
          patientId: patient.id,
          doctorId: Number(values.doctorId),
          roomId: Number(values.roomId),
        });
      else if (!admission)
        throw new Error(
          "This admission is no longer open. Refresh the record.",
        );
      else if (kind === "transfer")
        await api(`/admissions/${admission.id}/transfer`, "POST", {
          roomId: Number(values.roomId),
          reason: values.reason,
          version: admission.version,
        });
      else if (kind === "discharge")
        await api(`/admissions/${admission.id}/discharge`, "POST", {
          version: admission.version,
        });
      else if (kind === "doctor")
        await api(`/admissions/${admission.id}/doctor`, "POST", {
          doctorId: Number(values.doctorId),
          version: admission.version,
        });
      else
        await api(`/admissions/${admission.id}/procedures`, "POST", {
          doctorId: Number(values.doctorId),
          medicalProcedureId: Number(values.medicalProcedureId),
          performedAt: new Date(values.performedAt).toISOString(),
          note: values.note,
        });
      await client.invalidateQueries();
      onClose();
    } catch (e) {
      setError(e as Error);
      await client.invalidateQueries();
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal title={name} onClose={onClose}>
      <div className="flow-patient">
        <span className="avatar">
          {patient.firstName[0]}
          {patient.lastName[0]}
        </span>
        <div>
          <strong>{fullName(patient)}</strong>
          <small>{patient.patientIdentifier}</small>
        </div>
      </div>
      {review ? (
        <>
          <div className="review">
            <span className="eyebrow">Review and confirm</span>
            <h3>
              {kind === "discharge"
                ? "Close this admission?"
                : "Confirm the details below."}
            </h3>
            <p>
              {kind === "discharge"
                ? "Discharge closes the active stay and releases the occupied bed. Confirm only after the discharge decision has been made by the responsible clinician."
                : "The system will recheck permissions, availability and the current record before saving."}
            </p>
            {values.roomId && (
              <p>
                Destination:{" "}
                <strong>
                  Room{" "}
                  {Array.isArray(rooms) &&
                    rooms.find((r) => r.id === Number(values.roomId))
                      ?.roomNumber}
                </strong>
              </p>
            )}
            {values.doctorId && (
              <p>
                Doctor:{" "}
                <strong>
                  {Array.isArray(doctors) &&
                    fullName(
                      doctors.find((d) => d.id === Number(values.doctorId)),
                    )}
                </strong>
              </p>
            )}
            {kind === "procedure" && (
              <p>
                Procedure:{" "}
                <strong>
                  {Array.isArray(procedures) &&
                    procedures.find(
                      (p) => p.id === Number(values.medicalProcedureId),
                    )?.procedureName}
                </strong>
              </p>
            )}
            {kind === "discharge" && (
              <p>
                Recorded procedure total:{" "}
                <strong>{money(active?.totalCost)}</strong>
              </p>
            )}
          </div>
          <ErrorBox error={error} />
          <div className="modal-actions">
            <button
              className="secondary"
              onClick={() =>
                kind === "discharge" ? onClose() : setReview(false)
              }
              disabled={busy}
            >
              {kind === "discharge" ? "Cancel" : "Back"}
            </button>
            <button className="primary" onClick={submit} disabled={busy}>
              {busy ? "Saving…" : "Confirm " + CONFIRMATIONS[kind]}
              <CheckCircle2 size={17} />
            </button>
          </div>
        </>
      ) : (
        <form
          className="workflow-form"
          onSubmit={(e) => {
            e.preventDefault();
            setReview(true);
          }}
        >
          {["admit", "doctor", "procedure"].includes(kind) && (
            <label>
              Attending / performing doctor
              <select
                aria-label="Attending / performing doctor"
                required
                value={values.doctorId}
                onChange={update("doctorId")}
              >
                <option value="">Select doctor…</option>
                {Array.isArray(doctors) &&
                  doctors
                    .filter(
                      (d) =>
                        d.active &&
                        (user.role !== "DOCTOR" || d.id === user.doctorId),
                    )
                    .map((d) => (
                      <option value={d.id} key={d.id}>
                        Dr. {fullName(d)}
                      </option>
                    ))}
              </select>
            </label>
          )}
          {["admit", "transfer"].includes(kind) && (
            <label>
              Destination room
              <select
                aria-label="Destination room"
                required
                value={values.roomId}
                onChange={update("roomId")}
              >
                <option value="">Select available room…</option>
                {Array.isArray(rooms) &&
                  rooms
                    .filter(
                      (r) =>
                        r.active &&
                        r.availableBeds > 0 &&
                        r.id !== active?.assignment?.roomId,
                    )
                    .map((r) => (
                      <option key={r.id} value={r.id}>
                        Room {r.roomNumber} · {r.availableBeds} free beds
                      </option>
                    ))}
              </select>
            </label>
          )}
          {kind === "transfer" && (
            <label>
              Reason for transfer
              <textarea
                required
                maxLength={500}
                value={values.reason}
                onChange={update("reason")}
              />
            </label>
          )}
          {kind === "procedure" && (
            <>
              <label>
                Procedure
                <select
                  aria-label="Procedure"
                  required
                  value={values.medicalProcedureId}
                  onChange={update("medicalProcedureId")}
                >
                  <option value="">Select procedure…</option>
                  {Array.isArray(procedures) &&
                    procedures
                      .filter((p) => p.active)
                      .map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.procedureName} · {money(p.currentCost)}
                        </option>
                      ))}
                </select>
              </label>
              <label>
                Performed at
                <input
                  required
                  type="datetime-local"
                  step="0.001"
                  value={values.performedAt}
                  onChange={update("performedAt")}
                />
              </label>
              <label>
                Notes
                <textarea
                  maxLength={2000}
                  value={values.note}
                  onChange={update("note")}
                />
              </label>
            </>
          )}
          <div className="modal-actions">
            <button type="button" className="secondary" onClick={onClose}>
              Cancel
            </button>
            <button className="primary">
              Review details
              <ArrowRight size={17} />
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
