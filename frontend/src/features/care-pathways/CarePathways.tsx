"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { api, activeDepartment } from "../../api";
import type { PatientDirectoryPage, WorkspaceList } from "../../api/contracts";
import { ErrorBox, Title } from "../../components/workspace";
import { CarePreviewTimeline } from "./CarePreviewTimeline";
import { PatientTaskEditor } from "./PatientTaskEditor";
import { buildTaskOverrides, validPatientTasks as tasksAreValid } from "./task-overrides";
import {
  DocumentActionReview,
  documentReviewComplete,
  reviewedDocumentActions,
  type ActionReview,
  type PatientDocumentDraft,
} from "./DocumentActionReview";
import type {
  CarePreview,
  CareTaskDefinition,
  CareTemplate,
  CareTemplateSummary,
} from "./types";

const blankTask = (): CareTaskDefinition => ({
  key: crypto.randomUUID(),
  title: "",
  description: "",
  ownerRole: "DOCTOR",
  assignedUserId: null,
  dueOffsetMinutes: 0,
  dependsOn: [],
});

export function CarePathways() {
  const params = useSearchParams();
  const patientId = Number(params.get("patientId")) || null;
  const sourceId = params.get("sourceId");
  const draftId = params.get("draftId");
  const department = activeDepartment();
  const client = useQueryClient();
  const workspaces = useQuery({
    queryKey: ["/workspaces", department],
    queryFn: () => api<WorkspaceList>("/workspaces"),
  });
  const timeZone = workspaces.data?.timeZone ?? "UTC";
  const templates = useQuery({
    queryKey: ["care-workflows", department],
    queryFn: () => api<CareTemplateSummary[]>("/care-workflows"),
  });
  const assignees = useQuery({
    queryKey: ["care-workflow-assignees", department],
    queryFn: () =>
      api<
        {
          id: number;
          displayName: string;
          role: CareTaskDefinition["ownerRole"];
        }[]
      >("/care-workflows/assignees"),
  });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const selected = useQuery({
    queryKey: ["care-workflow", department, selectedId],
    queryFn: () => api<CareTemplate>(`/care-workflows/${selectedId}`),
    enabled: selectedId !== null,
  });
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [triggers, setTriggers] = useState<
    ("MANUAL" | "ADMISSION" | "DISCHARGE")[]
  >(["MANUAL"]);
  const [tasks, setTasks] = useState<CareTaskDefinition[]>([blankTask()]);
  const [version, setVersion] = useState<number | null>(null);
  const [savedDraftFingerprint, setSavedDraftFingerprint] = useState<string | null>(null);
  const [preview, setPreview] = useState<CarePreview | null>(null);
  const [previewRevision, setPreviewRevision] = useState<string | null>(null);
  const [patientTasks, setPatientTasks] = useState<CareTaskDefinition[]>([]);
  const [launchPatientId, setLaunchPatientId] = useState(
    patientId ? String(patientId) : "",
  );
  const [patientSearch, setPatientSearch] = useState("");
  const patientResults = useQuery({
    queryKey: ["care-patient-search", department, patientSearch],
    queryFn: () =>
      api<PatientDirectoryPage>(
        `/patients?q=${encodeURIComponent(patientSearch)}&page=0&size=10`,
      ),
    enabled: patientSearch.trim().length >= 2,
  });
  const [summary, setSummary] = useState("");
  const [sourceReference, setSourceReference] = useState(sourceId ?? "");
  const documentDraft = useQuery({
    queryKey: ["patient-document-draft", department, draftId],
    queryFn: () =>
      api<PatientDocumentDraft>(`/assistant/patient-drafts/${draftId}`),
    enabled: !!draftId,
  });
  const [actionReviews, setActionReviews] = useState<
    Record<string, ActionReview>
  >({});
  const [reviewed, setReviewed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [notice, setNotice] = useState("");

  function load(template: CareTemplate) {
    setSelectedId(template.id);
    setName(template.name);
    setDescription(template.description);
    setTriggers(template.draft.triggers);
    setTasks(template.draft.tasks);
    setVersion(template.version);
    setSavedDraftFingerprint(JSON.stringify({
      name: template.name,
      description: template.description,
      triggers: template.draft.triggers,
      tasks: template.draft.tasks,
    }));
    setPatientTasks(
      template.publishedVersions.find(
        (published) => published.version === template.published_version,
      )?.definition.tasks ?? [],
    );
    setPreview(null);
    setReviewed(false);
  }
  async function choose(id: number) {
    try {
      load(await api<CareTemplate>(`/care-workflows/${id}`));
    } catch (cause) {
      setError(cause as Error);
    }
  }
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      await action();
      await client.invalidateQueries();
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusy(false);
    }
  }
  function updateTask(index: number, patch: Partial<CareTaskDefinition>) {
    setTasks((current) =>
      current.map((task, position) =>
        position === index ? { ...task, ...patch } : task,
      ),
    );
    setPreview(null);
    setReviewed(false);
  }
  const validTasks =
    tasks.length > 0 &&
    tasks.every(
      (task) =>
        task.title.trim() &&
        task.key.trim() &&
        Number.isFinite(task.dueOffsetMinutes),
    );
  const canEdit = !!name.trim() && validTasks;
  const draftDirty = savedDraftFingerprint !== JSON.stringify({
    name,
    description,
    triggers,
    tasks,
  });
  const documentPatientMatches =
    !draftId ||
    (!!documentDraft.data &&
      documentDraft.data.patientId === Number(launchPatientId) &&
      (!sourceId || sourceId === documentDraft.data.source.id));
  const actionsReviewed =
    !draftId || documentReviewComplete(documentDraft.data, actionReviews);
  const selectedDocumentActions = reviewedDocumentActions(
    documentDraft.data,
    actionReviews,
  );
  const validDocumentActions = selectedDocumentActions.every(
    (action) =>
      action.decision === "REJECTED" ||
      (action.title.trim() && (!action.dueTime || action.dueDate)),
  );
  const publishedTasks =
    selected.data?.publishedVersions.find(
      (published) => published.version === selected.data.published_version,
    )?.definition.tasks ?? [];
  const taskOverrides = buildTaskOverrides(patientTasks, publishedTasks);
  const validPatientTasks = tasksAreValid(patientTasks, publishedTasks);
  const reviewRevision = JSON.stringify({
    patientId: launchPatientId,
    workflowVersion: selected.data?.published_version,
    taskOverrides,
    actions: selectedDocumentActions,
  });
  const finalPreviewReady = previewRevision === reviewRevision;
  async function saveDraft() {
    if (!canEdit) return;
    await run(async () => {
      const body = {
        name: name.trim(),
        description: description.trim(),
        triggers,
        tasks,
        version: version ?? 0,
      };
      const saved = await api<CareTemplate>(
        selectedId ? `/care-workflows/${selectedId}` : "/care-workflows",
        selectedId ? "PUT" : "POST",
        body,
      );
      load(saved);
      setNotice("Draft saved for review.");
    });
  }
  async function publish() {
    if (!selectedId || version === null) return;
    await run(async () => {
      await api(`/care-workflows/${selectedId}/publish`, "POST", { version });
      load(await api<CareTemplate>(`/care-workflows/${selectedId}`));
      setNotice("Published version is ready to preview and launch.");
    });
  }
  async function previewForPatient() {
    if (!selectedId || !launchPatientId || !selected.data?.published_version || !validPatientTasks)
      return;
    await run(async () => {
      const result = await api<CarePreview>(
        `/care-workflows/${selectedId}/preview`,
        "POST",
        {
          patientId: Number(launchPatientId),
          trigger: "MANUAL",
          workflowVersion: selected.data.published_version,
          taskOverrides,
          patientDraftId: draftId && actionsReviewed ? draftId : null,
          reviewedFollowUpActions:
            draftId && actionsReviewed ? selectedDocumentActions : [],
        },
      );
      setPreview(result);
      setPreviewRevision(draftId && !actionsReviewed ? null : reviewRevision);
      setReviewed(false);
      setNotice(
        "Review the full timeline, owners, dependencies and due times before approval.",
      );
    });
  }
  async function launch() {
    if (
      !selectedId ||
      !preview ||
      !reviewed ||
      !documentPatientMatches ||
      !actionsReviewed ||
      !validDocumentActions ||
      !finalPreviewReady ||
      (!!summary.trim() && !preview.portalSummaryConsentActive)
    )
      return;
    await run(async () => {
      await api(`/care-workflows/${selectedId}/launch`, "POST", {
        workflowVersion: preview.workflowVersion,
        patientId: preview.patientId,
        admissionId: preview.admissionId,
        idempotencyKey: crypto.randomUUID(),
        sourceReference:
          documentDraft.data?.source.id ?? (sourceReference.trim() || null),
        patientSummary: summary.trim(),
        patientDraftId: draftId && documentDraft.data ? draftId : null,
        taskOverrides,
        reviewedFollowUpActions: draftId ? selectedDocumentActions : [],
        approved: true,
      });
      setPreview(null);
      setReviewed(false);
      setNotice("Pathway launched and added to the patient timeline.");
    });
  }

  return (
    <div className="care-studio">
      <Title
        eyebrow="Care pathway studio"
        title="Plan the follow-up."
        description="Build repeatable task templates, review a patient timeline and approve each launch."
      />
      <div className="care-studio-layout">
        <aside
          className="care-studio-list panel"
          aria-label="Workflow templates"
        >
          <div className="section-heading">
            <h2>Templates</h2>
            <button
              className="secondary"
              onClick={() => {
                setSelectedId(null);
                setName("");
                setDescription("");
                setTriggers(["MANUAL"]);
                setTasks([blankTask()]);
                setVersion(null);
                setSavedDraftFingerprint(null);
                setPreview(null);
              }}
            >
              New draft
            </button>
          </div>
          {templates.isLoading && <p>Loading templates…</p>}
          <ErrorBox error={templates.error} />
          {(templates.data ?? []).map((template) => (
            <button
              key={template.id}
              className={`care-template-option${selectedId === template.id ? " active" : ""}`}
              onClick={() => void choose(template.id)}
            >
              <strong>{template.name}</strong>
              <small>
                {template.published_version
                  ? `Published v${template.published_version}`
                  : "Draft only"}
              </small>
            </button>
          ))}
          {!templates.isLoading && !templates.data?.length && (
            <p>No templates yet. Create a draft to start.</p>
          )}
        </aside>
        <div className="care-studio-main">
          <section className="panel care-editor">
            <div className="section-heading">
              <div>
                <h2>{selectedId ? "Edit draft" : "New workflow draft"}</h2>
                <p>
                  Tasks are clinician-authored follow-up steps. No orders or
                  treatments are created here.
                </p>
              </div>
              {version !== null && (
                <span className="status">Draft v{version}</span>
              )}
            </div>
            <div className="care-editor-fields">
              <label>
                Template name
                <input
                  value={name}
                  maxLength={120}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
              <label>
                Purpose
                <textarea
                  value={description}
                  maxLength={1000}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </label>
            </div>
            <fieldset className="care-trigger-field">
              <legend>Allowed triggers</legend>
              {(["MANUAL", "ADMISSION", "DISCHARGE"] as const).map(
                (trigger) => (
                  <label key={trigger}>
                    <input
                      type="checkbox"
                      checked={triggers.includes(trigger)}
                      onChange={(event) =>
                        setTriggers((current) =>
                          event.target.checked
                            ? [...current, trigger]
                            : current.filter((item) => item !== trigger),
                        )
                      }
                    />
                    {trigger.toLowerCase()}
                  </label>
                ),
              )}
            </fieldset>
            <div className="section-heading">
              <h3>Ordered tasks</h3>
              <button
                className="secondary"
                onClick={() => setTasks((current) => [...current, blankTask()])}
              >
                Add task
              </button>
            </div>
            {tasks.map((task, index) => (
              <div className="care-task-editor" key={task.key}>
                <div className="section-heading">
                  <strong>Step {index + 1}</strong>
                  <button
                    className="text-button"
                    disabled={tasks.length === 1}
                    onClick={() =>
                      setTasks((current) =>
                        current.filter((_, position) => position !== index),
                      )
                    }
                  >
                    Remove
                  </button>
                </div>
                <div className="care-task-fields">
                  <label>
                    Task title
                    <input
                      value={task.title}
                      maxLength={160}
                      onChange={(event) =>
                        updateTask(index, { title: event.target.value })
                      }
                    />
                  </label>
                  <label>
                    Owner role
                    <select
                      value={task.ownerRole}
                      onChange={(event) =>
                        updateTask(index, {
                          ownerRole: event.target
                            .value as CareTaskDefinition["ownerRole"],
                          assignedUserId: null,
                        })
                      }
                    >
                      <option value="DOCTOR">Doctor</option>
                      <option value="MEDICAL_STAFF">Medical staff</option>
                      <option value="ADMIN">Department admin</option>
                    </select>
                  </label>
                  <label>
                    Assign to
                    <select
                      value={task.assignedUserId ?? ""}
                      onChange={(event) =>
                        updateTask(index, {
                          assignedUserId: event.target.value
                            ? Number(event.target.value)
                            : null,
                        })
                      }
                    >
                      <option value="">
                        Unassigned until care team assigns
                      </option>
                      {(assignees.data ?? [])
                        .filter((person) => person.role === task.ownerRole)
                        .map((person) => (
                          <option key={person.id} value={person.id}>
                            {person.displayName}
                          </option>
                        ))}
                    </select>
                  </label>
                  <label>
                    Due after launch, minutes
                    <input
                      type="number"
                      min="0"
                      value={task.dueOffsetMinutes}
                      onChange={(event) =>
                        updateTask(index, {
                          dueOffsetMinutes: Number(event.target.value),
                        })
                      }
                    />
                  </label>
                  <label>
                    Depends on
                    <select
                      value={task.dependsOn[0] ?? ""}
                      onChange={(event) =>
                        updateTask(index, {
                          dependsOn: event.target.value
                            ? [event.target.value]
                            : [],
                        })
                      }
                    >
                      <option value="">No dependency</option>
                      {tasks.slice(0, index).map((preceding) => (
                        <option key={preceding.key} value={preceding.key}>
                          {preceding.title ||
                            `Step ${tasks.indexOf(preceding) + 1}`}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                <label>
                  Instructions
                  <textarea
                    value={task.description}
                    maxLength={1000}
                    onChange={(event) =>
                      updateTask(index, { description: event.target.value })
                    }
                  />
                </label>
              </div>
            ))}
            <div className="care-editor-actions">
              <button
                className="secondary"
                disabled={busy || !canEdit}
                onClick={saveDraft}
              >
                Save draft
              </button>
              <button
                className="primary"
                disabled={busy || !selectedId || version === null || draftDirty}
                onClick={publish}
              >
                Publish reviewed version
              </button>
            </div>
            {selectedId && draftDirty && (
              <p>Save the draft changes before publishing this version.</p>
            )}
          </section>
          {selectedId && (
            <section className="panel care-launch">
              <h2>Preview for a patient</h2>
              <p>
                Choose an authorized patient, review the timeline, then approve
                the launch.
              </p>
              <label>
                Find a patient
                <input
                  value={patientSearch}
                  onChange={(event) => {
                    setPatientSearch(event.target.value);
                    setLaunchPatientId("");
                    setPreview(null);
                  }}
                  placeholder="Name or patient ID"
                />
              </label>
              {patientResults.data && (
                <div className="care-patient-options">
                  {patientResults.data.items.map((patient) => (
                    <button
                      className="secondary"
                      key={patient.id}
                      onClick={() => {
                        setLaunchPatientId(String(patient.id));
                        setPatientSearch(
                          `${patient.firstName} ${patient.lastName} · ${patient.patientIdentifier}`,
                        );
                        setPreview(null);
                        setReviewed(false);
                      }}
                    >
                      {patient.firstName} {patient.lastName} ·{" "}
                      {patient.patientIdentifier}
                    </button>
                  ))}
                </div>
              )}
              {launchPatientId && (
                <p>Selected patient record #{launchPatientId}</p>
              )}
              {draftId && documentDraft.isLoading && (
                <p>Loading the document suggestions…</p>
              )}
              <ErrorBox error={documentDraft.error} />
              {draftId && documentDraft.data && !documentPatientMatches && (
                <p role="alert">
                  This document draft belongs to patient #
                  {documentDraft.data.patientId}. Select that patient before
                  launching its actions.
                </p>
              )}
              {patientTasks.length > 0 && (
                <PatientTaskEditor
                  tasks={patientTasks}
                  assignees={assignees.data ?? []}
                  onChange={(next) => {
                    setPatientTasks(next);
                    setPreviewRevision(null);
                    setReviewed(false);
                  }}
                />
              )}
              <button
                className="secondary"
                disabled={
                  busy || !launchPatientId || !selected.data?.published_version || !validPatientTasks
                }
                onClick={previewForPatient}
              >
                Generate preview
              </button>
              {!selected.data?.published_version && (
                <p>Publish a version before previewing a patient launch.</p>
              )}
              {preview && (
                <>
                  {documentDraft.data && (
                    <DocumentActionReview
                      draft={documentDraft.data}
                      reviews={actionReviews}
                      onChange={(next) => {
                        setActionReviews(next);
                        setPreviewRevision(null);
                        setReviewed(false);
                      }}
                      templateTasks={preview.tasks}
                      assignees={assignees.data ?? []}
                    />
                  )}
                  <CarePreviewTimeline
                    preview={preview}
                    timeZone={timeZone}
                    assignees={assignees.data ?? []}
                  />
                  {!finalPreviewReady && (
                    <button
                      className="secondary"
                      disabled={
                        busy || !documentPatientMatches || !validDocumentActions || !validPatientTasks
                      }
                      onClick={previewForPatient}
                    >
                      Refresh patient preview
                    </button>
                  )}
                  <div className="care-editor-fields">
                    {!draftId && (
                      <label>
                        Source reference ID, if used
                        <input
                          value={sourceReference}
                          onChange={(event) =>
                            setSourceReference(event.target.value)
                          }
                          placeholder="Owned assistant source ID"
                        />
                      </label>
                    )}
                    <label>
                      Patient-facing summary
                      <textarea
                        value={summary}
                        onChange={(event) => setSummary(event.target.value)}
                        placeholder="Only clinician-approved follow-up details. Leave blank to keep this internal."
                      />
                    </label>
                    {!!summary.trim() &&
                      !preview.portalSummaryConsentActive && (
                        <p role="alert">
                          This patient has not consented to portal follow-up
                          summaries. Leave the summary blank or record their
                          consent first.
                        </p>
                      )}
                  </div>
                  <label className="care-approval">
                    <input
                      type="checkbox"
                      checked={reviewed}
                      disabled={!finalPreviewReady || !actionsReviewed}
                      onChange={(event) => setReviewed(event.target.checked)}
                    />
                    I reviewed the patient, owners, due times, source, and
                    patient-facing text.
                  </label>
                  <button
                    className="primary"
                    disabled={
                      busy ||
                      !reviewed ||
                      !documentPatientMatches ||
                      !actionsReviewed ||
                      !validDocumentActions ||
                      !finalPreviewReady ||
                      (!!summary.trim() && !preview.portalSummaryConsentActive)
                    }
                    onClick={launch}
                  >
                    Approve and launch pathway
                  </button>
                </>
              )}
            </section>
          )}
          {notice && (
            <p role="status" className="care-notice">
              {notice}
            </p>
          )}
          <ErrorBox error={error || selected.error || workspaces.error} />
        </div>
      </div>
    </div>
  );
}
