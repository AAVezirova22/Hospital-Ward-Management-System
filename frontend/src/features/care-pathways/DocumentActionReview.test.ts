import { describe, expect, it } from "vitest";
import {
  documentReviewComplete,
  reviewedDocumentActions,
  type PatientDocumentDraft,
} from "./DocumentActionReview";
import { formatDue, isOverdue } from "./due";

const draft: PatientDocumentDraft = {
  draftId: "draft-one",
  patientId: 17,
  source: {
    id: "source-one",
    name: "Referral.txt",
    expiresAt: "2026-10-01T00:00:00Z",
  },
  followUpActions: [
    {
      actionId: "action-one",
      title: "Arrange follow-up",
      dueDate: null,
      dueTime: null,
      confidence: 0.72,
      status: "UNCERTAIN",
      requiresResolution: true,
      sources: [
        {
          name: "Referral.txt",
          location: "characters 10-27",
          reportedLocation: null,
          excerpt: "Arrange follow-up",
        },
      ],
      conflicts: [],
    },
    {
      actionId: "action-two",
      title: "Call patient",
      dueDate: "2026-09-30",
      dueTime: null,
      confidence: 0.91,
      status: "SUGGESTED",
      requiresResolution: false,
      sources: [
        {
          name: "Referral.txt",
          location: "characters 40-52",
          reportedLocation: null,
          excerpt: "Call patient",
        },
      ],
      conflicts: [],
    },
  ],
};

describe("clinician document action review", () => {
  it("requires an explicit decision for every extracted action", () => {
    expect(documentReviewComplete(draft, {})).toBe(false);
    expect(
      documentReviewComplete(draft, {
        "action-one": { kind: "REJECTED" },
      }),
    ).toBe(false);
    expect(
      documentReviewComplete(draft, {
        "action-one": { kind: "REJECTED" },
        "action-two": { kind: "REJECTED" },
      }),
    ).toBe(true);
  });

  it("submits rejected actions for audit without making them tasks", () => {
    expect(
      reviewedDocumentActions(draft, {
        "action-one": { kind: "REJECTED" },
        "action-two": {
          kind: "INCLUDED",
          selection: {
            actionId: "action-two",
            title: "Call patient",
            dueDate: "2026-09-30",
            dueTime: null,
            decision: "ACCEPTED",
            ownerRole: "DOCTOR",
            assignedUserId: 3,
            dependsOn: [],
          },
        },
      }),
    ).toEqual([
      { actionId: "action-one", decision: "REJECTED" },
      {
        actionId: "action-two",
        title: "Call patient",
        dueDate: "2026-09-30",
        dueTime: null,
        decision: "ACCEPTED",
        ownerRole: "DOCTOR",
        assignedUserId: 3,
        dependsOn: [],
      },
    ]);
  });

  it("keeps missing and date-only deadlines distinct", () => {
    expect(formatDue({ dueAt: null, dueOn: null })).toBe(
      "No due date specified",
    );
    expect(formatDue({ dueAt: null, dueOn: "2026-09-30" })).toBe(
      "2026-09-30 (date only)",
    );
    expect(isOverdue({ dueAt: null, dueOn: null })).toBe(false);
  });
});
