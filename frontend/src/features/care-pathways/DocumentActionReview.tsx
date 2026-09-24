"use client";

import type { CareTaskDefinition } from "./types";

export type DocumentEvidence = {
  name: string;
  location: string | null;
  reportedLocation: string | null;
  excerpt: string;
};

export type DocumentActionCandidate = {
  title: string;
  dueDate: string | null;
  dueTime: string | null;
  confidence: number;
  source: DocumentEvidence;
};

export type DocumentAction = {
  actionId: string;
  title: string;
  dueDate: string | null;
  dueTime: string | null;
  confidence: number;
  status: "SUGGESTED" | "UNCERTAIN" | "CONFLICT";
  sources: DocumentEvidence[];
  conflicts: DocumentActionCandidate[];
  requiresResolution: boolean;
};

export type PatientDocumentDraft = {
  draftId: string;
  source: { id: string; name: string; expiresAt: string };
  followUpActions: DocumentAction[];
  patientId?: number | null;
};

export type ReviewedDocumentAction = {
  actionId: string;
  title: string;
  dueDate: string | null;
  dueTime: string | null;
  decision: "ACCEPTED" | "EDITED";
  ownerRole: CareTaskDefinition["ownerRole"];
  assignedUserId: number | null;
  dependsOn: string[];
};
export type SubmittedDocumentAction =
  ReviewedDocumentAction | { actionId: string; decision: "REJECTED" };

export type ActionReview =
  | { kind: "REJECTED" }
  | { kind: "INCLUDED"; selection: ReviewedDocumentAction };

export function reviewedDocumentActions(
  draft: PatientDocumentDraft | undefined,
  reviews: Record<string, ActionReview>,
): SubmittedDocumentAction[] {
  return (draft?.followUpActions ?? []).flatMap<SubmittedDocumentAction>(
    (action) => {
      const review = reviews[action.actionId];
      if (!review) return [];
      if (review.kind === "INCLUDED") return [review.selection];
      return [{ actionId: action.actionId, decision: "REJECTED" }];
    },
  );
}

export function documentReviewComplete(
  draft: PatientDocumentDraft | undefined,
  reviews: Record<string, ActionReview>,
) {
  return (
    !!draft &&
    draft.followUpActions.every((action) => !!reviews[action.actionId])
  );
}

export function DocumentActionReview({
  draft,
  reviews,
  onChange,
  templateTasks,
  assignees,
}: {
  draft: PatientDocumentDraft;
  reviews: Record<string, ActionReview>;
  onChange: (reviews: Record<string, ActionReview>) => void;
  templateTasks: CareTaskDefinition[];
  assignees: {
    id: number;
    displayName: string;
    role: CareTaskDefinition["ownerRole"];
  }[];
}) {
  function include(action: DocumentAction, candidate: DocumentActionCandidate) {
    onChange({
      ...reviews,
      [action.actionId]: {
        kind: "INCLUDED",
        selection: {
          actionId: action.actionId,
          title: candidate.title,
          dueDate: candidate.dueDate,
          dueTime: candidate.dueTime,
          decision: "ACCEPTED",
          ownerRole: "DOCTOR",
          assignedUserId: null,
          dependsOn: [],
        },
      },
    });
  }

  function edit(actionId: string, patch: Partial<ReviewedDocumentAction>) {
    const review = reviews[actionId];
    if (review?.kind !== "INCLUDED") return;
    onChange({
      ...reviews,
      [actionId]: {
        kind: "INCLUDED",
        selection: {
          ...review.selection,
          ...patch,
          decision:
            patch.title !== undefined ||
            patch.dueDate !== undefined ||
            patch.dueTime !== undefined
              ? "EDITED"
              : review.selection.decision,
        },
      },
    });
  }

  return (
    <section className="document-action-review">
      <div className="section-heading">
        <div>
          <h3>Review document actions</h3>
          <p>
            {draft.source.name} · {draft.followUpActions.length} suggestion
            {draft.followUpActions.length === 1 ? "" : "s"}. Check each source
            and choose whether it belongs in this plan.
          </p>
        </div>
      </div>
      {draft.followUpActions.length === 0 && (
        <p>No explicit follow-up action was found in the document.</p>
      )}
      {draft.followUpActions.map((action) => {
        const review = reviews[action.actionId];
        const selected = review?.kind === "INCLUDED" ? review.selection : null;
        const candidates: DocumentActionCandidate[] = [
          {
            title: action.title,
            dueDate: action.dueDate,
            dueTime: action.dueTime,
            confidence: action.confidence,
            source: action.sources[0],
          },
          ...action.conflicts,
        ];
        return (
          <article className="document-action" key={action.actionId}>
            <div className="section-heading">
              <strong>{action.title}</strong>
              <span className="status">{action.status.toLowerCase()}</span>
            </div>
            {candidates.map((candidate, index) => (
              <div className="document-action-candidate" key={index}>
                <p>
                  <strong>{candidate.title}</strong> · Due{" "}
                  {candidate.dueDate ?? "unspecified"}
                  {candidate.dueTime ? ` at ${candidate.dueTime}` : ""}
                </p>
                <blockquote>
                  “{candidate.source.excerpt}”
                  <small>
                    {candidate.source.name} · {candidate.source.location}
                    {candidate.source.reportedLocation
                      ? ` · ${candidate.source.reportedLocation}`
                      : ""}
                  </small>
                </blockquote>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => include(action, candidate)}
                >
                  Use this cited action
                </button>
              </div>
            ))}
            <button
              type="button"
              className="text-button"
              onClick={() =>
                onChange({
                  ...reviews,
                  [action.actionId]: { kind: "REJECTED" },
                })
              }
            >
              Reject this suggestion
            </button>
            {review?.kind === "REJECTED" && <p>Rejected from this launch.</p>}
            {selected && (
              <div className="document-action-edit">
                <p>
                  {selected.decision === "EDITED"
                    ? "Clinician edited this suggestion."
                    : "Cited action selected."}{" "}
                  Date and time stay blank unless you confirm them.
                </p>
                <div className="care-task-fields">
                  <label>
                    Action title
                    <input
                      value={selected.title}
                      maxLength={200}
                      onChange={(event) =>
                        edit(action.actionId, { title: event.target.value })
                      }
                    />
                  </label>
                  <label>
                    Due date
                    <input
                      type="date"
                      value={selected.dueDate ?? ""}
                      onChange={(event) =>
                        edit(action.actionId, {
                          dueDate: event.target.value || null,
                          dueTime: event.target.value ? selected.dueTime : null,
                        })
                      }
                    />
                  </label>
                  <label>
                    Due time, if explicit
                    <input
                      type="time"
                      disabled={!selected.dueDate}
                      value={selected.dueTime ?? ""}
                      onChange={(event) =>
                        edit(action.actionId, {
                          dueTime: event.target.value || null,
                        })
                      }
                    />
                  </label>
                  <label>
                    Owner role
                    <select
                      value={selected.ownerRole}
                      onChange={(event) =>
                        edit(action.actionId, {
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
                      value={selected.assignedUserId ?? ""}
                      onChange={(event) =>
                        edit(action.actionId, {
                          assignedUserId: event.target.value
                            ? Number(event.target.value)
                            : null,
                        })
                      }
                    >
                      <option value="">Unassigned</option>
                      {assignees
                        .filter((person) => person.role === selected.ownerRole)
                        .map((person) => (
                          <option key={person.id} value={person.id}>
                            {person.displayName}
                          </option>
                        ))}
                    </select>
                  </label>
                </div>
                <fieldset className="care-trigger-field">
                  <legend>Depends on</legend>
                  {templateTasks.map((task) => (
                    <label key={task.key}>
                      <input
                        type="checkbox"
                        checked={selected.dependsOn.includes(task.key)}
                        onChange={(event) =>
                          edit(action.actionId, {
                            dependsOn: event.target.checked
                              ? [...selected.dependsOn, task.key]
                              : selected.dependsOn.filter(
                                  (key) => key !== task.key,
                                ),
                          })
                        }
                      />
                      {task.title}
                    </label>
                  ))}
                  {!templateTasks.length && <p>No template dependencies.</p>}
                </fieldset>
              </div>
            )}
          </article>
        );
      })}
    </section>
  );
}
