"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, activeDepartment } from "../../api";
import type { WorkspaceList } from "../../api/contracts";
import { ErrorBox, Link } from "../../components/workspace";
import type { CarePreview, CareRun, CareTaskDefinition } from "./types";
import { formatDue, isOverdue } from "./due";
import { CarePreviewTimeline } from "./CarePreviewTimeline";
import { PatientTaskEditor } from "./PatientTaskEditor";
import { buildTaskOverrides, validPatientTasks } from "./task-overrides";

type RunSummary = Pick<
  CareRun,
  | "id"
  | "templateId"
  | "workflowVersion"
  | "patientId"
  | "admissionId"
  | "trigger"
  | "status"
  | "sourceReference"
  | "reviewedAt"
  | "launchedAt"
  | "cancelledAt"
  | "version"
>;
const dateTime = (value: string) => new Date(value).toLocaleString();

function PendingReview({
  run,
  onApproved,
  timeZone,
}: {
  run: RunSummary;
  onApproved: () => Promise<void>;
  timeZone: string;
}) {
  const preview = useQuery({
    queryKey: ["pending-care-preview", run.id],
    queryFn: () =>
      api<CarePreview>(`/care-workflows/${run.templateId}/preview`, "POST", {
        workflowVersion: run.workflowVersion,
        patientId: run.patientId,
        admissionId: run.admissionId,
        trigger: run.trigger,
      }),
  });
  const assignees = useQuery({
    queryKey: ["care-workflow-assignees", activeDepartment()],
    queryFn: () =>
      api<
        {
          id: number;
          displayName: string;
          role: CareTaskDefinition["ownerRole"];
        }[]
      >("/care-workflows/assignees"),
  });
  const [patientTasks, setPatientTasks] = useState<CareTaskDefinition[] | null>(null);
  const [reviewedPreview, setReviewedPreview] = useState<CarePreview | null>(null);
  const [previewRevision, setPreviewRevision] = useState<string | null>(null);
  const [sourceReference, setSourceReference] = useState("");
  const [patientSummary, setPatientSummary] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const originalTasks = preview.data?.tasks ?? [];
  const editedTasks = patientTasks ?? originalTasks;
  const taskOverrides = buildTaskOverrides(editedTasks, originalTasks);
  const revision = JSON.stringify(taskOverrides);
  const previewReady = taskOverrides.length === 0 || previewRevision === revision;
  const timeline =
    previewRevision === revision ? reviewedPreview ?? preview.data : preview.data;
  const tasksValid = validPatientTasks(editedTasks, originalTasks);
  const summaryNeedsConsent =
    !!patientSummary.trim() && !timeline?.portalSummaryConsentActive;
  async function refreshPreview() {
    if (!preview.data || !tasksValid) return;
    setBusy(true);
    setError(null);
    try {
      const result = await api<CarePreview>(
        `/care-workflows/${run.templateId}/preview`,
        "POST",
        {
          workflowVersion: run.workflowVersion,
          patientId: run.patientId,
          admissionId: run.admissionId,
          trigger: run.trigger,
          taskOverrides,
        },
      );
      setReviewedPreview(result);
      setPreviewRevision(revision);
      setConfirmed(false);
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusy(false);
    }
  }
  async function approve() {
    if (!confirmed || !previewReady || !tasksValid || summaryNeedsConsent) return;
    setBusy(true);
    setError(null);
    try {
      await api(`/care-workflow-runs/${run.id}/approve`, "POST", {
        approved: true,
        sourceReference: sourceReference.trim() || null,
        patientSummary: patientSummary.trim(),
        taskOverrides,
      });
      await onApproved();
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="pending-care-review">
      <h4>Review before launch</h4>
      <p>
        This admission or discharge proposed a pathway. No task has started.
      </p>
      <ErrorBox error={preview.error || assignees.error || error} />
      {preview.isLoading && <p>Loading the proposed timeline…</p>}
      {preview.data && (
        <>
          <PatientTaskEditor
            tasks={editedTasks}
            assignees={assignees.data ?? []}
            onChange={(next) => {
              setPatientTasks(next);
              setConfirmed(false);
            }}
          />
          {(!previewReady || summaryNeedsConsent) && (
            <button
              className="secondary"
              disabled={busy || !tasksValid}
              onClick={() => void refreshPreview()}
            >
              Refresh patient preview
            </button>
          )}
          {!previewReady && (
            <p role="status">Review the refreshed timeline before approving your edits.</p>
          )}
          <CarePreviewTimeline preview={timeline ?? preview.data} timeZone={timeZone} assignees={assignees.data ?? []} />
          <div className="care-editor-fields">
            <label>
              Owned source ID, if used
              <input
                value={sourceReference}
                onChange={(event) => setSourceReference(event.target.value)}
              />
            </label>
            <label>
              Patient-facing summary
              <textarea
                value={patientSummary}
                onChange={(event) => setPatientSummary(event.target.value)}
                placeholder="Leave blank to keep this plan internal."
              />
            </label>
          </div>
          {summaryNeedsConsent && (
            <p role="alert">
              The patient has not consented to portal follow-up summaries.
              Leave the summary blank or record consent first.
            </p>
          )}
          <label className="care-approval">
            <input
              type="checkbox"
              checked={confirmed}
              disabled={!previewReady || !tasksValid || summaryNeedsConsent}
              onChange={(event) => setConfirmed(event.target.checked)}
            />
            I reviewed this patient's tasks, owners, dates, source, and summary.
          </label>
          <button
            className="primary"
            disabled={busy || !confirmed || !previewReady || !tasksValid || summaryNeedsConsent}
            onClick={() => void approve()}
          >
            Approve and launch tasks
          </button>
        </>
      )}
    </div>
  );
}

export function PatientPathways({ patientId }: { patientId: number }) {
  const department = activeDepartment();
  const client = useQueryClient();
  const workspaces = useQuery({
    queryKey: ["/workspaces", department],
    queryFn: () => api<WorkspaceList>("/workspaces"),
  });
  const timeZone = workspaces.data?.timeZone ?? "UTC";
  const runs = useQuery({
    queryKey: ["care-workflow-runs", department, patientId],
    queryFn: () =>
      api<RunSummary[]>(`/care-workflow-runs?patientId=${patientId}`),
  });
  const [openId, setOpenId] = useState<number | null>(null);
  const detail = useQuery({
    queryKey: ["care-workflow-run", department, openId],
    queryFn: () => api<CareRun>(`/care-workflow-runs/${openId}`),
    enabled: openId !== null,
  });
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  async function refresh() {
    await client.invalidateQueries({ queryKey: ["care-workflow-runs"] });
    await client.invalidateQueries({ queryKey: ["care-workflow-run"] });
    await client.invalidateQueries({ queryKey: ["care-tasks"] });
  }
  async function cancel(run: RunSummary) {
    if (!confirmCancel) return;
    setBusy(true);
    setError(null);
    try {
      await api(`/care-workflow-runs/${run.id}/cancel`, "POST", {
        version: run.version,
      });
      setConfirmCancel(false);
      await refresh();
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="patient-pathways">
      <div className="section-heading">
        <div>
          <h2>Follow-up pathways</h2>
          <p>Approved task plans, owners and overdue work for this patient.</p>
        </div>
        <Link
          className="secondary"
          to={`/app/care-pathways?patientId=${patientId}`}
        >
          Plan pathway
        </Link>
      </div>
      {runs.isLoading && <p>Loading pathways…</p>}
      <ErrorBox error={runs.error || detail.error || workspaces.error || error} />
      {!runs.isLoading && !runs.data?.length && (
        <div className="panel care-empty">
          <h3>No follow-up pathway yet</h3>
          <p>
            A clinician can preview a published template and approve a plan for
            this patient.
          </p>
        </div>
      )}
      {(runs.data ?? []).map((run) => (
        <article className="panel patient-pathway-run" key={run.id}>
          <div className="section-heading">
            <div>
              <span className="status">{run.status.replaceAll("_", " ")}</span>
              <h3>
                Pathway #{run.id} · Template v{run.workflowVersion}
              </h3>
              <p>
                {run.reviewedAt
                  ? `Approved ${dateTime(run.reviewedAt)}`
                  : "Awaiting clinician review"}
                {run.sourceReference ? ` · Source: ${run.sourceReference}` : ""}
              </p>
            </div>
            <button
              className="secondary"
              aria-expanded={openId === run.id}
              onClick={() => {
                setOpenId(openId === run.id ? null : run.id);
                setConfirmCancel(false);
              }}
            >
              {openId === run.id ? "Hide timeline" : "View timeline"}
            </button>
          </div>
          {openId === run.id && detail.data && (
            <>
              {run.status === "PENDING_REVIEW" ? (
                <PendingReview run={run} onApproved={refresh} timeZone={timeZone} />
              ) : (
                <ol className="care-preview-list">
                  {detail.data.tasks.map((task) => (
                    <li key={task.id}>
                      <strong>{task.title}</strong>
                      <p>{task.description}</p>
                      <small>
                        {task.status.replaceAll("_", " ")} ·{" "}
                        {task.ownerRole.replaceAll("_", " ")} · Due{" "}
                        {formatDue(task, timeZone)} · {task.dependencyState}
                      </small>
                      {task.taskOrigin === "DOCUMENT" && task.sourceExcerpt && (
                        <blockquote className="care-task-source">
                          “{task.sourceExcerpt}”
                          <small>{task.sourceLocation}</small>
                        </blockquote>
                      )}
                      {isOverdue(task, timeZone) &&
                        task.status !== "COMPLETED" &&
                        task.status !== "CANCELLED" && (
                          <span className="care-overdue"> · Overdue</span>
                        )}
                    </li>
                  ))}
                </ol>
              )}
              {(run.status === "ACTIVE" || run.status === "PENDING_REVIEW") && (
                <div className="care-cancel">
                  <label>
                    <input
                      type="checkbox"
                      checked={confirmCancel}
                      onChange={(event) =>
                        setConfirmCancel(event.target.checked)
                      }
                    />
                    Cancel this pathway and stop future reminders. History stays
                    visible.
                  </label>
                  <button
                    className="secondary"
                    disabled={!confirmCancel || busy}
                    onClick={() => void cancel(run)}
                  >
                    Cancel pathway
                  </button>
                </div>
              )}
            </>
          )}
        </article>
      ))}
    </section>
  );
}
