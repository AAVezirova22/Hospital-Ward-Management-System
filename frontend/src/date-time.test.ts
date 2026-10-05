import { describe, expect, it } from "vitest";
import {
  dateInTimeZone,
  dateTimeInTimeZone,
  appointmentTime,
} from "./date-time";

describe("dateInTimeZone", () => {
  it("keeps report dates on the department's calendar day", () => {
    expect(
      dateInTimeZone(new Date("2025-03-09T04:30:00.000Z"), "America/New_York"),
    ).toBe("2025-03-08");
  });
  it("formats appointment wall times in the department zone rather than the browser zone", () => {
    const instant = new Date("2026-11-02T14:30:00Z");
    expect(dateTimeInTimeZone(instant, "Europe/Sofia")).toBe(
      "2026-11-02T16:30",
    );
    expect(dateTimeInTimeZone(instant, "America/New_York")).toBe(
      "2026-11-02T09:30",
    );
    expect(appointmentTime(instant.toISOString(), "Europe/Sofia")).toContain(
      "16:30",
    );
  });
});
