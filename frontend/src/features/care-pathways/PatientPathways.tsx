"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, activeDepartment } from "../../api";
import { ErrorBox, Link } from "../../components/workspace";
import type { CarePreview, CareRun } from "./types";
import { formatDue, isOverdue } from "./due";

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
}: {
  run: RunSummary;
  onApproved: () => Promise<void>;
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
  const [sourceReference, setSourceReference] = useState("");
  const [patientSummary, setPatientSummary] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  async function approve() {
    if (!confirmed) return;
    setBusy(true);
    setError(null);
    try {
      await api(`/care-workflow-runs/${run.id}/approve`, "POST", {
        approved: true,
        sourceReference: sourceReference.trim() || null,
        patientSummary: patientSummary.trim(),
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
      <ErrorBox error={preview.error || error} />
      {preview.isLoading && <p>Loading the proposed timeline…</p>}
      {preview.data && (
        <>
          <ol className="care-preview-list">
            {preview.data.tasks.map((task) => (
              <li key={task.key}>
                <strong>{task.title}</strong>
                <p>{task.description}</p>
                <small>
                  {task.ownerRole.replaceAll("_", " ")} · Due {formatDue(task)}{" "}
                  · {task.dependencyState}
                </small>
              </li>
            ))}
          </ol>
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
          <label className="care-approval">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
            />
            I reviewed this patient's tasks, owners, dates, source, and summary.
          </label>
          <button
            className="primary"
            disabled={busy || !confirmed}
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
      <ErrorBox error={runs.error || detail.error || error} />
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
                <PendingReview run={run} onApproved={refresh} />
              ) : (
                <ol className="care-preview-list">
                  {detail.data.tasks.map((task) => (
                    <li key={task.id}>
                      <strong>{task.title}</strong>
                      <p>{task.description}</p>
                      <small>
                        {task.status.replaceAll("_", " ")} ·{" "}
                        {task.ownerRole.replaceAll("_", " ")} · Due{" "}
                        {formatDue(task)} · {task.dependencyState}
                      </small>
                      {task.taskOrigin === "DOCUMENT" && task.sourceExcerpt && (
                        <blockquote className="care-task-source">
                          “{task.sourceExcerpt}”
                          <small>{task.sourceLocation}</small>
                        </blockquote>
                      )}
                      {isOverdue(task) &&
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
