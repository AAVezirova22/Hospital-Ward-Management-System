import { describe, expect, it } from "vitest";
import { dateInTimeZone } from "./date-time";

describe("dateInTimeZone", () => {
  it("keeps report dates on the department's calendar day", () => {
    expect(
      dateInTimeZone(new Date("2025-03-09T04:30:00.000Z"), "America/New_York"),
    ).toBe("2025-03-08");
  });
});
