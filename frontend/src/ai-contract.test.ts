import { describe, it, expect } from "vitest";
import { aiResponse, safeRoute } from "./ai-contract";
const base = { message: "Result", sessionId: "session-1", model: "test" };
describe("assistant response boundaries", () => {
  it("accepts populated workflow fields and structured text data in Zod 4", () => {
    const result = aiResponse.parse({
      ...base,
      responseType: "WORKFLOW_PROPOSAL",
      data: {
        action: {
          id: 1,
          actionType: "WORKFLOW",
          expiresAt: "2026-09-20T12:00:00Z",
          status: "PENDING",
        },
        workflow: {
          title: "Create a patient",
          steps: [
            {
              key: "patient",
              operation: "createPatient",
              source: "admissions.csv",
              fields: { firstName: "Vera", active: true, count: 2 },
            },
          ],
        },
      },
    });
    expect(result.responseType).toBe("WORKFLOW_PROPOSAL");
    for (const responseType of ["TEXT", "ERROR", "REPORT_RESULT"]) {
      expect(
        aiResponse.parse({
          ...base,
          responseType,
          data: { detail: "Result", count: 3 },
        }).data,
      ).toEqual({ detail: "Result", count: 3 });
    }
  });
  it("preserves per-field source evidence for clinician review", () => {
    const result = aiResponse.parse({
      ...base,
      responseType: "WORKFLOW_PROPOSAL",
      data: {
        action: {
          id: 8,
          actionType: "WORKFLOW",
          expiresAt: "2026-09-30T12:00:00Z",
          status: "PENDING",
        },
        workflow: {
          title: "Review referral",
          steps: [
            {
              key: "patient",
              operation: "createPatient",
              source: "referral.txt",
              fields: { firstName: "Vera" },
              evidence: {
                firstName: {
                  status: "UNCERTAIN",
                  confidence: 0.42,
                  requiresDecision: true,
                  sources: [
                    {
                      sourceId: "source-1",
                      sourceName: "referral.txt",
                      location: "characters 0-4",
                      reportedLocation: "page 1",
                      excerpt: "Vera",
                      verified: true,
                      characterStart: 0,
                      characterEnd: 4,
                    },
                  ],
                  conflicts: [],
                },
              },
            },
          ],
        },
      },
    });
    if (result.responseType !== "WORKFLOW_PROPOSAL")
      throw new Error("Wrong response type");
    expect(result.data.workflow.steps[0].evidence.firstName).toMatchObject({
      requiresDecision: true,
      sources: [{ verified: true, location: "characters 0-4" }],
    });
  });
  it("accepts authoritative room lists", () => {
    expect(
      aiResponse.parse({
        ...base,
        responseType: "ROOM_LIST",
        data: {
          rooms: [
            {
              id: 1,
              roomNumber: "304",
              availableBeds: 2,
              bedCount: 4,
              capabilities: ["oxygen"],
            },
          ],
          requiredCapabilities: ["oxygen"],
          excludedRooms: [
            {
              id: 2,
              roomNumber: "305",
              missingCapabilities: ["oxygen"],
              reason: "Missing required capabilities: oxygen",
            },
          ],
        },
      }),
    ).toMatchObject({
      responseType: "ROOM_LIST",
      data: {
        excludedRooms: [
          {
            roomNumber: "305",
            reason: "Missing required capabilities: oxygen",
          },
        ],
      },
    });
  });
  it("rejects negative capacity and malformed room lists", () => {
    for (const data of [
      { rooms: "unknown" },
      { rooms: [{ id: 1, roomNumber: "304", availableBeds: -1, bedCount: 4 }] },
    ])
      expect(() =>
        aiResponse.parse({ ...base, responseType: "ROOM_LIST", data }),
      ).toThrow();
  });
  it("never accepts executable or external navigation", () => {
    for (const route of [
      "javascript:alert(1)",
      "https://example.com",
      "//example.com",
      "/app/users/../../admin",
      "/app/patients/1?script=x",
    ])
      expect(safeRoute.safeParse(route).success).toBe(false);
  });
  it("accepts only known internal routes", () => {
    expect(safeRoute.parse("/app/patients/42")).toBe("/app/patients/42");
    expect(safeRoute.parse("/app/patients/PAT-0001")).toBe(
      "/app/patients/PAT-0001",
    );
    expect(safeRoute.parse("/app/reports")).toBe("/app/reports");
    expect(safeRoute.parse("/app/care-pathways")).toBe("/app/care-pathways");
    expect(safeRoute.parse("/app/tasks")).toBe("/app/tasks");
  });
  it("rejects unknown response types", () => {
    expect(() =>
      aiResponse.parse({
        ...base,
        responseType: "EXECUTE_SCRIPT",
        data: { code: "alert(1)" },
      }),
    ).toThrow();
  });
  it("requires an owned pending action shape for confirmation UI", () => {
    expect(() =>
      aiResponse.parse({
        ...base,
        responseType: "CONFIRMATION_CARD",
        data: { action: { id: -1, actionType: "DELETE_ALL" } },
      }),
    ).toThrow();
  });
});
