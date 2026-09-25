import { afterEach, describe, expect, it, vi } from "vitest";
import { previewDayGroups } from "./CarePreviewTimeline";
import { dueSort, isOverdue } from "./due";
import { buildTaskOverrides } from "./task-overrides";
import type { CarePreview, CareTaskDefinition } from "./types";

const task = (key: string): CareTaskDefinition => ({
  key,
  title: key,
  description: "",
  ownerRole: "DOCTOR",
  assignedUserId: 7,
  dueOffsetMinutes: 60,
  dependsOn: [],
});

describe("patient pathway review", () => {
  afterEach(() => vi.useRealTimers());

  it("groups timed and date-only tasks by the department's calendar day", () => {
    const preview: CarePreview = {
      templateId: 1,
      workflowVersion: 2,
      patientId: 3,
      admissionId: null,
      trigger: "MANUAL",
      baseTime: "2026-09-25T10:00:00Z",
      portalSummaryConsentActive: false,
      tasks: [
        { ...task("after-midnight"), dueAt: "2026-09-25T23:30:00Z", dependencyState: "READY" },
        { ...task("date-only"), dueAt: null, dueOn: "2026-09-25", dependencyState: "READY" },
        { ...task("unscheduled"), dueAt: null, dependencyState: "READY" },
      ],
    };

    expect(previewDayGroups(preview, "Europe/Kyiv").map((group) => group.day)).toEqual([
      "2026-09-25",
      "2026-09-26",
      null,
    ]);
  });

  it("sends an explicit null when a clinician clears an assignee", () => {
    const original = task("call");
    expect(buildTaskOverrides([{ ...original, assignedUserId: null }], [original])).toEqual([
      { key: "call", assignedUserId: null },
    ]);
  });

  it("checks date-only overdue work in the department time zone", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-26T00:30:00Z"));
    const due = { dueAt: null, dueOn: "2026-09-25" };
    expect(isOverdue(due, "America/New_York")).toBe(false);
    expect(isOverdue(due, "Asia/Tokyo")).toBe(true);
  });

  it("orders date-only and timed tasks by the department calendar", () => {
    const dateOnly = { dueAt: null, dueOn: "2026-09-25" };
    const afterMidnight = { dueAt: "2026-09-25T23:30:00Z" };
    expect(dueSort(dateOnly, "Europe/Kyiv")).toBeLessThan(
      dueSort(afterMidnight, "Europe/Kyiv"),
    );
  });
});
