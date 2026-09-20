import { describe, it, expect } from "vitest";
import { aiResponse, safeRoute } from "./ai-contract";
const base = { message: "Result", sessionId: "session-1", model: "test" };
describe("assistant response boundaries", () => {
  it("accepts authoritative room lists", () => {
    expect(
      aiResponse.parse({
        ...base,
        responseType: "ROOM_LIST",
        data: {
          rooms: [{ id: 1, roomNumber: "304", availableBeds: 2, bedCount: 4 }],
        },
      }).responseType,
    ).toBe("ROOM_LIST");
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
    expect(safeRoute.parse("/app/reports")).toBe("/app/reports");
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
