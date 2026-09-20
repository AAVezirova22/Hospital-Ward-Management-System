import { describe, expect, it } from "vitest";
import { defaultProcedureTime } from "./workflow-time";

describe("default procedure time", () => {
  it("uses the current local time when already within an admission", () => {
    const now = new Date("2026-09-20T11:50:30.250Z");
    expect(
      new Date(defaultProcedureTime("2026-09-20T11:48:41Z", now)).getTime(),
    ).toBe(now.getTime());
  });
  it("does not precede a server admission when the browser clock is behind", () => {
    const value = defaultProcedureTime(
      "2026-09-20T11:48:41.947504131Z",
      new Date("2026-09-20T11:48:41.087Z"),
    );
    expect(new Date(value).toISOString()).toBe("2026-09-20T11:48:41.948Z");
  });
  it("keeps millisecond precision in datetime-local inputs", () => {
    const now = new Date("2026-09-20T11:50:30.257Z");
    expect(defaultProcedureTime(undefined, now)).toMatch(
      /T\d{2}:\d{2}:30\.257$/,
    );
  });
  it("ignores an invalid admission timestamp without creating an invalid default", () => {
    const now = new Date("2026-09-20T11:50:30.250Z");
    expect(new Date(defaultProcedureTime("invalid", now)).getTime()).toBe(
      now.getTime(),
    );
  });
});
