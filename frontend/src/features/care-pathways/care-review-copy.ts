/**
 * Plain-language copy for the care pathway review surface.
 *
 * Every condition that can disable a control must also explain itself on
 * screen, so the wording lives here as pure functions: it can be unit tested
 * without a DOM, and it stays in one place instead of drifting between the
 * buttons it describes.
 */

export type LaunchGate = {
  /** The document draft belongs to the patient selected for this launch. */
  documentPatientMatches: boolean;
  /** Every included document action still has a usable title and due time. */
  validDocumentActions: boolean;
  /** The patient level task overrides are complete. */
  validPatientTasks: boolean;
  /** Every suggested document action has been accepted or rejected. */
  actionsReviewed: boolean;
  /** The visible preview still matches the inputs it was generated from. */
  finalPreviewReady: boolean;
  /** The clinician ticked the approval checkbox. */
  reviewed: boolean;
};

const PATIENT_TASKS_BLOCKED =
  "Every patient task needs a title and a due offset between 0 and 525600 minutes before a preview can be generated.";

/**
 * The notice under a generated preview. A preview generated before the
 * document actions are reviewed silently omits every document sourced follow-up,
 * so say so instead of only inviting a general read-through.
 */
export function previewNotice(documentActionsExcluded: boolean) {
  const review =
    "Review the full timeline, owners, dependencies and due times before approval.";
  return documentActionsExcluded
    ? `Document actions are left out of this preview until every suggestion is accepted or rejected. ${review}`
    : review;
}

/** Explains a disabled "Generate preview" button. */
export function previewGateMessage(gate: Pick<LaunchGate, "validPatientTasks">) {
  return gate.validPatientTasks ? null : PATIENT_TASKS_BLOCKED;
}

/** Explains a disabled approval checkbox. */
export function approvalGateMessage(
  gate: Pick<LaunchGate, "actionsReviewed" | "finalPreviewReady">,
) {
  if (!gate.actionsReviewed)
    return "Accept or reject every document action to enable approval.";
  if (!gate.finalPreviewReady)
    return "Generate a fresh patient preview before approving this launch.";
  return null;
}

/**
 * Explains a disabled "Refresh patient preview" button. This control is
 * blocked by the same document conditions as the launch button, so it needs its
 * own on-screen reason: a clinician whose action title was blanked would
 * otherwise be stuck with an inert button and no way back.
 */
export function refreshGateMessage(
  gate: Pick<
    LaunchGate,
    "documentPatientMatches" | "validDocumentActions" | "validPatientTasks"
  >,
) {
  if (!gate.documentPatientMatches)
    return "Select the patient this document draft belongs to before refreshing.";
  if (!gate.validDocumentActions)
    return "Fix the included document action fields before refreshing the preview.";
  if (!gate.validPatientTasks) return PATIENT_TASKS_BLOCKED;
  return null;
}

/**
 * Explains a disabled "Approve and launch pathway" button. Consent for the
 * patient facing summary is reported separately as an alert, so it is not
 * repeated here.
 */
export function launchGateMessage(gate: LaunchGate) {
  if (!gate.documentPatientMatches)
    return "Select the patient this document draft belongs to before launching.";
  if (!gate.actionsReviewed)
    return "Accept or reject every document action before launching.";
  if (!gate.validDocumentActions)
    return "Give every included document action a title, plus a due date when it has a due time, before launching.";
  if (!gate.validPatientTasks) return PATIENT_TASKS_BLOCKED;
  if (!gate.finalPreviewReady)
    return "Generate a fresh patient preview, then review it before approving.";
  if (!gate.reviewed)
    return "Confirm the review checkbox to approve this launch.";
  return null;
}

export type NamedPatient = {
  firstName: string;
  lastName: string;
  patientIdentifier: string;
};

/**
 * Names the launch patient. The entry links only carry an internal integer, so
 * an approving clinician must still see whose record they are approving.
 */
export function patientSelectionLabel(
  recordId: string,
  patient?: NamedPatient | null,
) {
  const name = patient ? `${patient.firstName} ${patient.lastName}`.trim() : "";
  const described = [name, patient?.patientIdentifier].filter(Boolean).join(" · ");
  return described
    ? `Selected patient: ${described} (record #${recordId}).`
    : `Selected patient record #${recordId}. Confirm the name before approving.`;
}

/** Document confidence is stored as a 0..1 score; tolerate a 0..100 scale too. */
export function formatConfidence(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(value))
    return "Confidence not recorded";
  const percent = value > 1 ? value : value * 100;
  return `Model confidence ${Math.round(Math.min(100, Math.max(0, percent)))}%`;
}

export type ActionFlag = {
  label: string;
  tone: "conflict" | "caution" | "neutral";
  note: string;
};

/**
 * Makes the state of a suggested action legible. A conflicting or unresolved
 * action is not an ordinary suggestion, and it is still accepted or rejected on
 * the same terms, so only the wording changes here.
 */
export function actionFlag(action: {
  status: "SUGGESTED" | "UNCERTAIN" | "CONFLICT";
  requiresResolution: boolean;
}) {
  if (action.status === "CONFLICT")
    return {
      label: "Conflict",
      tone: "conflict",
      note: "The document says different things about this action. Compare the cited options below, keep the one the record supports, or reject the suggestion.",
    } satisfies ActionFlag;
  if (action.status === "UNCERTAIN" || action.requiresResolution)
    return {
      label: "Unresolved",
      tone: "caution",
      note: "This suggestion is not settled. Check it against the cited source, correct the fields, or reject it.",
    } satisfies ActionFlag;
  return { label: "Suggested", tone: "neutral", note: "" } satisfies ActionFlag;
}

/**
 * Per row outcome for a care task change. Reports what the service actually
 * stored rather than what was asked for, so a change the service declined is
 * never reported as a success.
 */
export function taskUpdateNotice(input: {
  change: "status" | "assignee";
  savedStatus: string;
  savedAssigneeId: number | null;
  savedAssigneeName: string | null;
  requestedAssigneeId: number | null;
}) {
  if (input.change === "status")
    return input.savedStatus === "COMPLETED"
      ? "Task marked complete."
      : input.savedStatus === "IN_PROGRESS"
        ? "Task started."
        : `Task status is now ${input.savedStatus}.`;
  const who =
    input.savedAssigneeId === null
      ? "nobody"
      : (input.savedAssigneeName ?? "the selected clinician");
  if (input.requestedAssigneeId === null && input.savedAssigneeId !== null)
    return `The service kept ${who} as the assignee, so this task is still assigned.`;
  if (input.requestedAssigneeId === null) return "Task is now unassigned.";
  return `Task assigned to ${who}.`;
}
