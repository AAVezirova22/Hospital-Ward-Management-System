import { describe, expect, it } from "vitest";
import {
  actionFlag,
  approvalGateMessage,
  formatConfidence,
  launchGateMessage,
  patientSelectionLabel,
  previewGateMessage,
  previewNotice,
  refreshGateMessage,
  taskUpdateNotice,
  type LaunchGate,
} from "./care-review-copy";

const openGate: LaunchGate = {
  documentPatientMatches: true,
  validDocumentActions: true,
  validPatientTasks: true,
  actionsReviewed: true,
  finalPreviewReady: true,
  reviewed: true,
};

const closed = (over: Partial<LaunchGate>): LaunchGate => ({ ...openGate, ...over });

describe("care pathway review copy", () => {
  it("says nothing while every control is enabled", () => {
    expect(launchGateMessage(openGate)).toBeNull();
    expect(approvalGateMessage(openGate)).toBeNull();
    expect(previewGateMessage(openGate)).toBeNull();
  });

  it("names the reason a launch is blocked, one condition at a time", () => {
    // documentPatientMatches false also disables "Refresh patient preview", so
    // both the launch and the refresh need an on-screen reason.
    expect(launchGateMessage(closed({ documentPatientMatches: false }))).toMatch(
      /document draft belongs to/i,
    );
    expect(launchGateMessage(closed({ actionsReviewed: false }))).toMatch(
      /accept or reject every document action/i,
    );
    expect(launchGateMessage(closed({ validDocumentActions: false }))).toMatch(
      /title.*due date/i,
    );
    expect(launchGateMessage(closed({ finalPreviewReady: false }))).toMatch(
      /fresh patient preview/i,
    );
    expect(launchGateMessage(closed({ reviewed: false }))).toMatch(/checkbox/i);
  });

  it("explains the approval checkbox separately from the launch button", () => {
    expect(approvalGateMessage(closed({ actionsReviewed: false }))).toMatch(
      /accept or reject every document action/i,
    );
    expect(approvalGateMessage(closed({ finalPreviewReady: false }))).toMatch(
      /fresh patient preview/i,
    );
    // A preview blocked by an incomplete patient task is a preview problem, not
    // an approval one.
    expect(approvalGateMessage(closed({ validPatientTasks: false }))).toBeNull();
    expect(previewGateMessage(closed({ validPatientTasks: false }))).toMatch(
      /due offset/i,
    );
  });

  it("explains an inert Refresh patient preview button", () => {
    // validDocumentActions false disables both the launch and the only recovery
    // control, so the refresh needs its own message.
    expect(
      refreshGateMessage(closed({ validDocumentActions: false })),
    ).toMatch(/included document action/i);
    expect(
      refreshGateMessage(closed({ documentPatientMatches: false })),
    ).toMatch(/document draft belongs to/i);
    expect(refreshGateMessage(openGate)).toBeNull();
    // A stale preview is refreshable, so it is not a blocked state.
    expect(refreshGateMessage(closed({ finalPreviewReady: false }))).toBeNull();
  });

  it("discloses that document actions are excluded from a preview", () => {
    // A preview generated before the review sends no draft and no reviewed
    // actions, so the notice must say the document follow-ups are missing.
    expect(previewNotice(true)).toMatch(/left out of this preview/i);
    expect(previewNotice(true)).toMatch(/accepted or rejected/i);
    expect(previewNotice(false)).not.toMatch(/left out/i);
    expect(previewNotice(false)).toMatch(/review the full timeline/i);
  });

  it("names the launch patient instead of only its internal id", () => {
    expect(
      patientSelectionLabel("42", {
        firstName: "Ada",
        lastName: "Okafor",
        patientIdentifier: "P-1",
      }),
    ).toBe("Selected patient: Ada Okafor · P-1 (record #42).");
    // While the lookup is in flight, still prompt for confirmation.
    expect(patientSelectionLabel("42")).toMatch(/record #42/);
    expect(patientSelectionLabel("42")).toMatch(/confirm the name/i);
  });

  it("renders a model confidence for every cited candidate", () => {
    expect(formatConfidence(0.82)).toBe("Model confidence 82%");
    expect(formatConfidence(82)).toBe("Model confidence 82%");
    expect(formatConfidence(0)).toBe("Model confidence 0%");
    expect(formatConfidence(null)).toBe("Confidence not recorded");
  });

  it("gives a conflicting action its own badge and an explanation", () => {
    const conflict = actionFlag({ status: "CONFLICT", requiresResolution: true });
    expect(conflict.label).toBe("Conflict");
    expect(conflict.tone).toBe("conflict");
    expect(conflict.note).toMatch(/different things about this action/i);

    // requiresResolution was previously received and never read; an UNCERTAIN
    // action and a flagged one must both be legible.
    expect(actionFlag({ status: "UNCERTAIN", requiresResolution: false })).toMatchObject({
      label: "Unresolved",
      tone: "caution",
    });
    expect(actionFlag({ status: "SUGGESTED", requiresResolution: true })).toMatchObject({
      label: "Unresolved",
      tone: "caution",
    });
    expect(
      actionFlag({ status: "SUGGESTED", requiresResolution: false }).note,
    ).toBe("");
  });

  it("reports the assignee the service actually stored", () => {
    expect(
      taskUpdateNotice({
        change: "assignee",
        savedStatus: "OPEN",
        savedAssigneeId: null,
        savedAssigneeName: null,
        requestedAssigneeId: null,
      }),
    ).toBe("Task is now unassigned.");
    // The service keeps the current assignee when the request cannot clear it,
    // so never report that as a completed unassignment.
    expect(
      taskUpdateNotice({
        change: "assignee",
        savedStatus: "OPEN",
        savedAssigneeId: 7,
        savedAssigneeName: "Dr Vance",
        requestedAssigneeId: null,
      }),
    ).toBe("The service kept Dr Vance as the assignee, so this task is still assigned.");
    expect(
      taskUpdateNotice({
        change: "assignee",
        savedStatus: "OPEN",
        savedAssigneeId: 9,
        savedAssigneeName: null,
        requestedAssigneeId: 9,
      }),
    ).toBe("Task assigned to the selected clinician.");
  });

  it("reports a status change from the stored status", () => {
    expect(
      taskUpdateNotice({
        change: "status",
        savedStatus: "COMPLETED",
        savedAssigneeId: 7,
        savedAssigneeName: "Dr Vance",
        requestedAssigneeId: 7,
      }),
    ).toBe("Task marked complete.");
    expect(
      taskUpdateNotice({
        change: "status",
        savedStatus: "IN_PROGRESS",
        savedAssigneeId: 7,
        savedAssigneeName: "Dr Vance",
        requestedAssigneeId: 7,
      }),
    ).toBe("Task started.");
  });
});
