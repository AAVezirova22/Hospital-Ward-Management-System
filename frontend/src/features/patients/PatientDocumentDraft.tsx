"use client";

import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { activeDepartment, api, patientHref } from "../../api";
import type { Patient, PatientDirectoryPage } from "../../api/contracts";
import { ErrorBox } from "../../components/workspace";
import { useAssistantSources } from "../assistant/AssistantSources";

type FieldKey =
  | "patientIdentifier"
  | "firstName"
  | "lastName"
  | "dateOfBirth"
  | "address"
  | "phoneNumber";
type Evidence = {
  name: string;
  location?: string | null;
  excerpt?: string | null;
};
type SuggestedField = {
  status: string;
  proposedValue: string | null;
  confidence: number | null;
  sources: Evidence[];
  conflicts: string[];
};
type Candidate = Pick<
  Patient,
  "id" | "patientIdentifier" | "firstName" | "lastName" | "dateOfBirth"
>;
type Draft = {
  draftId?: string;
  source: { id: string; name: string; expiresAt: string };
  fields: Record<FieldKey, SuggestedField>;
  matchCandidates: Candidate[];
  followUpActions?: {
    actionId: string;
    title: string;
    dueDate: string | null;
    dueTime: string | null;
    confidence: number | null;
    status: string;
    sources: Evidence[];
    requiresResolution: boolean;
  }[];
};
type Disclosure = {
  mode: string;
  model: string | null;
  sendsDocumentsToExternalProvider: boolean;
  disclosure: string;
};

const labels: Record<FieldKey, string> = {
  patientIdentifier: "Patient ID",
  firstName: "First name",
  lastName: "Last name",
  dateOfBirth: "Date of birth",
  address: "Address",
  phoneNumber: "Phone number",
};
const required: FieldKey[] = [
  "patientIdentifier",
  "firstName",
  "lastName",
  "dateOfBirth",
];
const fieldKeys = Object.keys(labels) as FieldKey[];
const emptyField = (): SuggestedField => ({
  status: "MISSING",
  proposedValue: null,
  confidence: null,
  sources: [],
  conflicts: [],
});

export function PatientDocumentDraft({
  initialPatient,
  onClose,
}: {
  initialPatient?: Patient;
  onClose: () => void;
}) {
  const router = useRouter();
  const client = useQueryClient();
  const sources = useAssistantSources();
  const disclosure = useQuery({
    queryKey: ["assistant-provider-disclosure"],
    queryFn: () => api<Disclosure>("/assistant/provider-disclosure"),
  });
  const [draft, setDraft] = useState<Draft | null>(null);
  const [savedPatient, setSavedPatient] = useState<Patient | null>(null);
  const [draftBound, setDraftBound] = useState(false);
  const [target, setTarget] = useState<"new" | number | null>(
    initialPatient?.id ?? null,
  );
  const [identitySearch, setIdentitySearch] = useState("");
  const [dragging, setDragging] = useState(false);
  const dragDepth = useRef(0);
  const identityResults = useQuery({
    queryKey: ["patient-draft-identity-search", activeDepartment(), identitySearch],
    queryFn: () =>
      api<PatientDirectoryPage>(
        `/patients?q=${encodeURIComponent(identitySearch)}&page=0&size=10`,
      ),
    enabled: identitySearch.trim().length >= 2,
  });
  const [values, setValues] = useState<Record<FieldKey, string>>({
    patientIdentifier: initialPatient?.patientIdentifier ?? "",
    firstName: initialPatient?.firstName ?? "",
    lastName: initialPatient?.lastName ?? "",
    dateOfBirth: initialPatient?.dateOfBirth ?? "",
    address: initialPatient?.address ?? "",
    phoneNumber: initialPatient?.phoneNumber ?? "",
  });
  const [accepted, setAccepted] = useState<FieldKey[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusy(false);
    }
  }

  async function extract() {
    const source = sources.sources[0];
    if (!source) return;
    await run(async () => {
      const result = await api<Draft>("/assistant/patient-drafts", "POST", {
        sourceId: source.id,
      });
      setDraft(result);
      setTarget(
        initialPatient?.id ?? (result.matchCandidates.length ? null : "new"),
      );
    });
  }

  function attachFile(file: File) {
    void run(async () => {
      await sources.clear();
      await sources.add(file);
      setDraft(null);
      setAccepted([]);
    });
  }

  function reviewManually() {
    const source = sources.sources[0];
    if (!source) return;
    setDraft({
      source,
      fields: Object.fromEntries(
        fieldKeys.map((key) => [key, emptyField()]),
      ) as Record<FieldKey, SuggestedField>,
      matchCandidates: [],
    });
    setTarget(initialPatient?.id ?? null);
    setError(null);
  }

  async function selectTarget(value: "new" | number) {
    if (value === "new") {
      setValues({
        patientIdentifier: "",
        firstName: "",
        lastName: "",
        dateOfBirth: "",
        address: "",
        phoneNumber: "",
      });
      setAccepted([]);
      setTarget(value);
      return;
    }
    await run(async () => {
      const candidate = draft?.matchCandidates.find(
        (item) => item.id === value,
      );
      const detail = await api<{ patient: Patient }>(
        `/patients/${encodeURIComponent(candidate?.patientIdentifier ?? String(value))}`,
      );
      const patient = detail.patient;
      setValues({
        patientIdentifier: patient.patientIdentifier,
        firstName: patient.firstName,
        lastName: patient.lastName,
        dateOfBirth: patient.dateOfBirth,
        address: patient.address ?? "",
        phoneNumber: patient.phoneNumber ?? "",
      });
      setAccepted([]);
      setTarget(value);
    });
  }

  async function save() {
    if (target === null || required.some((key) => !values[key].trim())) return;
    await run(async () => {
      let record: Patient | undefined = initialPatient;
      if (typeof target === "number" && record?.id !== target) {
        const candidate = draft?.matchCandidates.find(
          (item) => item.id === target,
        );
        const detail = await api<{ patient: Patient }>(
          `/patients/${encodeURIComponent(candidate?.patientIdentifier ?? String(target))}`,
        );
        record = detail.patient;
      }
      const payload = {
        ...values,
        address: values.address || null,
        phoneNumber: values.phoneNumber || null,
        version: record?.version ?? null,
      };
      const saved = await api<Patient>(
        record ? `/patients/${record.id}` : "/patients",
        record ? "PUT" : "POST",
        payload,
      );
      setSavedPatient(saved);
      if (draft?.draftId) {
        await api(`/assistant/patient-drafts/${draft.draftId}/patient`, "PUT", {
          patientId: saved.id,
        });
        setDraftBound(true);
      }
      await client.invalidateQueries();
    });
  }

  return (
    <section
      className="pathway-import panel"
      aria-label="Review patient document"
    >
      <div className="section-heading">
        <div>
          <h2>Review a patient document</h2>
          <p>Only confirmed fields are saved to the patient record.</p>
        </div>
        <button className="secondary" onClick={onClose}>
          Close
        </button>
      </div>
      <div className="pathway-import-steps">
        <span>1. Choose source</span>
        <span>2. Resolve identity</span>
        <span>3. Review fields</span>
      </div>
      {savedPatient ? (
        <div className="pathway-next">
          <h3>Patient details approved</h3>
          <p>
            The reviewed values are saved. You can now choose a published
            pathway and review its patient timeline.
          </p>
          <div className="care-editor-actions">
            {draft?.draftId && !draftBound && (
              <button
                className="secondary"
                disabled={busy}
                onClick={() =>
                  void run(async () => {
                    await api(
                      `/assistant/patient-drafts/${draft.draftId}/patient`,
                      "PUT",
                      {
                        patientId: savedPatient.id,
                      },
                    );
                    setDraftBound(true);
                  })
                }
              >
                Retry document link
              </button>
            )}
            <button
              className="primary"
              disabled={!!draft?.draftId && !draftBound}
              onClick={() => {
                const source = sources.sources[0];
                if (source) sources.release(source.id);
                onClose();
                const params = new URLSearchParams({
                  patientId: String(savedPatient.id),
                });
                if (source) params.set("sourceId", source.id);
                if (draft?.draftId) params.set("draftId", draft.draftId);
                router.push(`/app/care-pathways?${params.toString()}`);
              }}
            >
              Continue to pathway
            </button>
            <button
              className="secondary"
              onClick={() => {
                onClose();
                router.push(patientHref(savedPatient));
              }}
            >
              Open patient dossier
            </button>
          </div>
        </div>
      ) : (
        <>
          <div
            className={`pathway-import-upload${dragging ? " is-dragging" : ""}`}
            onDragEnter={(event) => {
              if (!event.dataTransfer.types.includes("Files")) return;
              event.preventDefault();
              dragDepth.current++;
              if (!busy && !draft) setDragging(true);
            }}
            onDragOver={(event) => {
              if (!event.dataTransfer.types.includes("Files")) return;
              event.preventDefault();
              event.dataTransfer.dropEffect = busy || draft ? "none" : "copy";
            }}
            onDragLeave={() => {
              dragDepth.current = Math.max(0, dragDepth.current - 1);
              if (dragDepth.current === 0) setDragging(false);
            }}
            onDrop={(event) => {
              event.preventDefault();
              dragDepth.current = 0;
              setDragging(false);
              if (busy || draft) return;
              const file = event.dataTransfer.files[0];
              if (file) attachFile(file);
            }}
          >
            <p>Drop a supported document here, or choose a file below.</p>
            <label>
              Referral, registration form, or discharge document
              <input
                type="file"
                accept=".pdf,.txt,.csv,.doc,.docx,.xls,.xlsx"
                disabled={busy || !!draft}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  event.target.value = "";
                  if (file) attachFile(file);
                }}
              />
            </label>
            {sources.sources.map((source) => (
              <p key={source.id}>
                {source.name} · Source expires{" "}
                {new Date(source.expiresAt).toLocaleString()}
              </p>
            ))}
            {disclosure.isLoading && <p>Checking the configured provider…</p>}
            {disclosure.data && (
              <p className="pathway-disclosure">{disclosure.data.disclosure}</p>
            )}
            <ErrorBox error={disclosure.error} />
            <div className="care-editor-actions">
              <button
                className="primary"
                disabled={
                  busy || !sources.sources.length || !disclosure.data || !!draft
                }
                onClick={extract}
              >
                Submit document for a reviewable draft
              </button>
              <button
                className="secondary"
                disabled={busy || !sources.sources.length || !!draft}
                onClick={reviewManually}
              >
                Review manually
              </button>
            </div>
          </div>
          {draft && (
            <>
              <div className="pathway-identity">
                <h3>Resolve patient identity</h3>
                <p>
                  Choose the existing record or explicitly start a new one.
                  Nothing is attached until you save.
                </p>
                {initialPatient ? (
                  <p>
                    Updating {initialPatient.firstName}{" "}
                    {initialPatient.lastName} (
                    {initialPatient.patientIdentifier})
                  </p>
                ) : (
                  <>
                    <label>
                      Search existing patient records
                      <input
                        value={identitySearch}
                        onChange={(event) =>
                          setIdentitySearch(event.target.value)
                        }
                        placeholder="Name or patient ID"
                      />
                    </label>
                    {identityResults.data?.items.map((candidate) => (
                      <label
                        key={`search-${candidate.id}`}
                        className="pathway-choice"
                      >
                        <input
                          type="radio"
                          name="patient-target"
                          checked={target === candidate.id}
                          onChange={() => void selectTarget(candidate.id)}
                        />
                        {candidate.firstName} {candidate.lastName} ·{" "}
                        {candidate.patientIdentifier} · Born{" "}
                        {candidate.dateOfBirth}
                      </label>
                    ))}
                    {draft.matchCandidates.map((candidate) => (
                      <label key={candidate.id} className="pathway-choice">
                        <input
                          type="radio"
                          name="patient-target"
                          checked={target === candidate.id}
                          onChange={() => void selectTarget(candidate.id)}
                        />
                        {candidate.firstName} {candidate.lastName} ·{" "}
                        {candidate.patientIdentifier} · Born{" "}
                        {candidate.dateOfBirth}
                      </label>
                    ))}
                    <label className="pathway-choice">
                      <input
                        type="radio"
                        name="patient-target"
                        checked={target === "new"}
                        disabled={
                          identitySearch.trim().length < 2 &&
                          draft.matchCandidates.length === 0
                        }
                        onChange={() => void selectTarget("new")}
                      />
                      Create a separate patient record after checking matches
                    </label>
                  </>
                )}
              </div>
              {target !== null && (
                <div className="pathway-fields">
                  <h3>Confirm every value</h3>
                  <p>
                    Use a suggestion only when its cited source supports it.
                    Resolve conflicting and missing fields yourself.
                  </p>
                  {fieldKeys.map((key) => {
                    const suggested = draft.fields[key];
                    return (
                      <div className="pathway-field" key={key}>
                        <div>
                          <label htmlFor={`draft-${key}`}>
                            {labels[key]}
                            {required.includes(key) ? " *" : ""}
                          </label>
                          <input
                            id={`draft-${key}`}
                            type={key === "dateOfBirth" ? "date" : "text"}
                            value={values[key]}
                            onChange={(event) =>
                              setValues((current) => ({
                                ...current,
                                [key]: event.target.value,
                              }))
                            }
                            required={required.includes(key)}
                          />
                        </div>
                        <div className="pathway-evidence">
                          <strong>
                            {suggested?.status ?? "Missing"}
                            {suggested?.confidence != null
                              ? ` · ${Math.round(suggested.confidence * 100)}% confidence`
                              : ""}
                          </strong>
                          {suggested?.proposedValue && (
                            <p>Suggested: {suggested.proposedValue}</p>
                          )}
                          {suggested?.sources?.map((source, index) => (
                            <blockquote key={index}>
                              <small>
                                {source.name}
                                {source.location ? ` · ${source.location}` : ""}
                              </small>
                              <p>{source.excerpt}</p>
                            </blockquote>
                          ))}
                          {suggested?.conflicts?.length > 0 && (
                            <p className="error">
                              Conflicting values:{" "}
                              {suggested.conflicts.join("; ")}
                            </p>
                          )}
                          {suggested?.proposedValue && (
                            <button
                              className="secondary"
                              type="button"
                              onClick={() => {
                                setValues((current) => ({
                                  ...current,
                                  [key]: suggested.proposedValue ?? "",
                                }));
                                setAccepted((current) => [
                                  ...new Set([...current, key]),
                                ]);
                              }}
                            >
                              Use cited value
                            </button>
                          )}
                          {accepted.includes(key) && (
                            <small>
                              Cited value selected. You can still edit it.
                            </small>
                          )}
                        </div>
                      </div>
                    );
                  })}
                  {!!draft.followUpActions?.length && (
                    <section className="pathway-action-evidence">
                      <h3>Follow-up actions found in the source</h3>
                      <p>
                        Review these against their excerpts when approving the
                        pathway. An uncertain action is never added
                        automatically.
                      </p>
                      {draft.followUpActions.map((action) => (
                        <article key={action.actionId}>
                          <strong>{action.title}</strong>
                          <p>
                            {action.dueDate
                              ? `Date in source: ${action.dueDate}`
                              : "No due date in source"}
                            {action.dueTime ? ` · Time: ${action.dueTime}` : ""}{" "}
                            · {action.status}
                          </p>
                          {action.sources.map((source, index) => (
                            <blockquote key={index}>
                              <small>
                                {source.name}
                                {source.location ? ` · ${source.location}` : ""}
                              </small>
                              <p>{source.excerpt}</p>
                            </blockquote>
                          ))}
                        </article>
                      ))}
                    </section>
                  )}
                  <div className="pathway-review-actions">
                    <p>
                      Saving requires your review. The source document remains
                      under the assistant retention limit.
                    </p>
                    <button
                      className="primary"
                      disabled={
                        busy || required.some((key) => !values[key].trim())
                      }
                      onClick={save}
                    >
                      {target === "new"
                        ? "Approve and create patient"
                        : "Approve and update patient"}
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </>
      )}
      <ErrorBox error={error} />
    </section>
  );
}
