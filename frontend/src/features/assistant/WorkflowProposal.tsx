"use client";
import { useEffect, useState } from "react";
import { date } from "../../api";
import { Status } from "../../components/workspace";
import type { AiResponse } from "../../ai-contract";

type Proposal = Extract<
  AiResponse,
  { responseType: "WORKFLOW_PROPOSAL" }
>["data"];
type FieldDecision = {
  stepKey: string;
  field: string;
  decision: "ACCEPTED" | "EDITED";
  value?: unknown;
};
type ReviewChoice = { decision: "ACCEPTED" | "EDITED"; input: string };
type Citation =
  Proposal["workflow"]["steps"][number]["evidence"][string]["sources"][number];
const reviewKey = (stepKey: string, field: string) =>
  `${stepKey}\u0000${field}`;
function initialInput(value: unknown) {
  if (typeof value === "string") return value;
  if (value == null) return "";
  return JSON.stringify(value);
}
function editedValue(input: string, original: unknown): unknown {
  if (typeof original === "boolean") {
    if (input !== "true" && input !== "false")
      throw new Error("Choose Yes or No.");
    return input === "true";
  }
  if (typeof original === "number") {
    const parsed = Number(input);
    if (!input.trim() || !Number.isFinite(parsed))
      throw new Error("Enter a valid number.");
    return parsed;
  }
  if (
    Array.isArray(original) ||
    (typeof original === "object" && original !== null)
  )
    return JSON.parse(input);
  return input;
}
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
  onAction: (
    operation: "confirm" | "cancel",
    body?: { fieldDecisions: FieldDecision[] },
  ) => void;
}) {
  const [expired, setExpired] = useState(
    Date.parse(data.action.expiresAt) <= Date.now(),
  );
  const [choices, setChoices] = useState<Record<string, ReviewChoice>>({});
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
  const requiredFields = data.workflow.steps.flatMap((step) =>
    Object.entries(step.evidence)
      .filter(([, evidence]) => evidence.requiresDecision)
      .map(([field]) => ({ step, field })),
  );
  const canConfirm = requiredFields.every(({ step, field }) => {
    const choice = choices[reviewKey(step.key, field)];
    if (!choice) return false;
    if (choice.decision === "ACCEPTED") return true;
    try {
      editedValue(choice.input, step.fields[field]);
      return true;
    } catch {
      return false;
    }
  });
  function choose(
    stepKey: string,
    field: string,
    value: unknown,
    decision: ReviewChoice["decision"],
  ) {
    const key = reviewKey(stepKey, field);
    setChoices((current) => ({
      ...current,
      [key]: { decision, input: current[key]?.input ?? initialInput(value) },
    }));
  }
  function confirm() {
    if (!canConfirm) return;
    onAction("confirm", {
      fieldDecisions: requiredFields.map(({ step, field }) => {
        const choice = choices[reviewKey(step.key, field)];
        return choice.decision === "EDITED"
          ? {
              stepKey: step.key,
              field,
              decision: "EDITED" as const,
              value: editedValue(choice.input, step.fields[field]),
            }
          : { stepKey: step.key, field, decision: "ACCEPTED" as const };
      }),
    });
  }
  function citation(
    cite: Citation,
    index: number,
    kind: "source" | "conflict",
  ) {
    return (
      <blockquote key={`${kind}-${index}`} className="workflow-citation">
        <p>“{cite.excerpt}”</p>
        <small>
          {cite.sourceName} · {cite.location}
          {cite.reportedLocation ? ` · Reported: ${cite.reportedLocation}` : ""}
          {cite.verified
            ? " · Verified in uploaded source"
            : " · Source not verified"}
        </small>
      </blockquote>
    );
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
              {Object.entries(step.fields).map(([key, value]) => {
                const evidence = step.evidence[key];
                const choice = choices[reviewKey(step.key, key)];
                return (
                  <div key={key} className="workflow-field-review">
                    <dt>{labels[key] || key}</dt>
                    <dd>
                      <strong>{display(value)}</strong>
                      {evidence && (
                        <div className="workflow-evidence">
                          <small>
                            {evidence.status.toLowerCase()} ·{" "}
                            {Math.round(evidence.confidence * 100)}% confidence
                            {evidence.requiresDecision
                              ? " · Decision required"
                              : ""}
                          </small>
                          {evidence.sources.map((cite, index) =>
                            citation(cite, index, "source"),
                          )}
                          {evidence.conflicts.length > 0 && (
                            <p>Conflicting source text</p>
                          )}
                          {evidence.conflicts.map((cite, index) =>
                            citation(cite, index, "conflict"),
                          )}
                          {evidence.requiresDecision && !done && (
                            <div className="workflow-field-decision">
                              <button
                                type="button"
                                className={
                                  choice?.decision === "ACCEPTED"
                                    ? "primary"
                                    : "secondary"
                                }
                                aria-label={`Accept ${labels[key] || key} in step ${step.key}`}
                                disabled={busy || expired}
                                onClick={() =>
                                  choose(step.key, key, value, "ACCEPTED")
                                }
                              >
                                Accept proposed value
                              </button>
                              <button
                                type="button"
                                className={
                                  choice?.decision === "EDITED"
                                    ? "primary"
                                    : "secondary"
                                }
                                aria-label={`Edit ${labels[key] || key} in step ${step.key}`}
                                disabled={busy || expired}
                                onClick={() =>
                                  choose(step.key, key, value, "EDITED")
                                }
                              >
                                Edit value
                              </button>
                              {choice?.decision === "EDITED" && (
                                <label>
                                  Corrected {labels[key] || key}
                                  {typeof value === "boolean" ? (
                                    <select
                                      value={choice.input}
                                      onChange={(event) =>
                                        setChoices((current) => ({
                                          ...current,
                                          [reviewKey(step.key, key)]: {
                                            decision: "EDITED",
                                            input: event.target.value,
                                          },
                                        }))
                                      }
                                    >
                                      <option value="true">Yes</option>
                                      <option value="false">No</option>
                                    </select>
                                  ) : Array.isArray(value) ||
                                    (typeof value === "object" &&
                                      value !== null) ? (
                                    <textarea
                                      value={choice.input}
                                      onChange={(event) =>
                                        setChoices((current) => ({
                                          ...current,
                                          [reviewKey(step.key, key)]: {
                                            decision: "EDITED",
                                            input: event.target.value,
                                          },
                                        }))
                                      }
                                    />
                                  ) : (
                                    <input
                                      type={
                                        typeof value === "number"
                                          ? "number"
                                          : "text"
                                      }
                                      value={choice.input}
                                      onChange={(event) =>
                                        setChoices((current) => ({
                                          ...current,
                                          [reviewKey(step.key, key)]: {
                                            decision: "EDITED",
                                            input: event.target.value,
                                          },
                                        }))
                                      }
                                    />
                                  )}
                                </label>
                              )}
                            </div>
                          )}
                        </div>
                      )}
                    </dd>
                  </div>
                );
              })}
            </dl>
          </li>
        ))}
      </ol>
      <p role="status">
        {expired
          ? "Proposal expired. Send a new request."
          : "Expires " + date(data.action.expiresAt)}
      </p>
      {requiredFields.length > 0 && !done && (
        <p role="status">
          {Object.keys(choices).length} of {requiredFields.length} fields
          reviewed. Check each cited value before confirming.
        </p>
      )}
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
            disabled={busy || expired || !canConfirm}
            onClick={confirm}
          >
            Confirm workflow
          </button>
        </div>
      )}
    </div>
  );
}
