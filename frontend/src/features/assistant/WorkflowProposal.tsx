"use client";
import { useEffect, useState } from "react";
import { date } from "../../api";
import { Status } from "../../components/workspace";
import type { AiResponse } from "../../ai-contract";

type Proposal = Extract<
  AiResponse,
  { responseType: "WORKFLOW_PROPOSAL" }
>["data"];
const operations: Record<string, string> = {
  createHospital: "Create hospital and department",
  createPatient: "Create patient",
  createDoctor: "Create doctor",
  createRoom: "Create room",
  createProcedure: "Create procedure",
  admit: "Admit patient",
  transfer: "Transfer patient",
  discharge: "Discharge patient",
  recordProcedure: "Record performed procedure",
};
const labels: Record<string, string> = {
  name: "Hospital name",
  departmentName: "Department",
  patientIdentifier: "Patient identifier",
  firstName: "First name",
  lastName: "Last name",
  dateOfBirth: "Date of birth",
  address: "Address",
  phoneNumber: "Phone",
  doctorIdentifier: "Doctor identifier",
  specialty: "Specialty",
  active: "Active",
  roomNumber: "Room",
  bedCount: "Beds",
  capabilities: "Room capabilities",
  requiredRoomCapabilities: "Required room capabilities",
  procedureCode: "Procedure code",
  procedureName: "Procedure",
  currentCost: "Cost",
  patientId: "Patient",
  doctorId: "Doctor",
  roomId: "Room",
  admissionId: "Admission",
  medicalProcedureId: "Procedure",
  performedAt: "Performed at",
  note: "Note",
  reason: "Reason",
  version: "Record version",
};
export function WorkflowProposal({
  data,
  done,
  busy,
  onAction,
}: {
  data: Proposal;
  done?: string;
  busy: boolean;
  onAction: (operation: "confirm" | "cancel") => void;
}) {
  const [expired, setExpired] = useState(
    Date.parse(data.action.expiresAt) <= Date.now(),
  );
  useEffect(() => {
    const timer = setInterval(
      () => setExpired(Date.parse(data.action.expiresAt) <= Date.now()),
      1000,
    );
    return () => clearInterval(timer);
  }, [data.action.expiresAt]);
  function display(value: unknown) {
    if (typeof value === "string" && value.startsWith("$")) {
      const index = data.workflow.steps.findIndex(
        (s) => s.key === value.slice(1),
      );
      if (index >= 0) return "Created in step " + (index + 1);
    }
    if (typeof value === "boolean") return value ? "Yes" : "No";
    if (value == null) return "Not provided";
    return typeof value === "object" ? JSON.stringify(value) : String(value);
  }
  return (
    <div className="confirmation workflow-proposal">
      <h3>{data.workflow.title}</h3>
      <p>
        All steps are applied together. If a step fails, no changes are saved.
      </p>
      <ol>
        {data.workflow.steps.map((step) => (
          <li key={step.key}>
            <strong>{operations[step.operation] || step.operation}</strong>
            <p>Source: {step.source}</p>
            <dl>
              {Object.entries(step.fields).map(([key, value]) => (
                <div key={key}>
                  <dt>{labels[key] || key}</dt>
                  <dd>{display(value)}</dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
      </ol>
      <p role="status">
        {expired
          ? "Proposal expired. Send a new request."
          : "Expires " + date(data.action.expiresAt)}
      </p>
      {done ? (
        <Status value={done} />
      ) : (
        <div className="actions">
          <button
            className="secondary"
            disabled={busy}
            onClick={() => onAction("cancel")}
          >
            Cancel proposal
          </button>
          <button
            className="primary"
            disabled={busy || expired}
            onClick={() => onAction("confirm")}
          >
            Confirm workflow
          </button>
        </div>
      )}
    </div>
  );
}
