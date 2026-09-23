import { describe, expect, it } from "vitest";
import { missingCapabilities, normalizeCapabilities } from "./room-capabilities";

describe("room capability matching", () => {
  it("normalizes flexible tags case-insensitively and removes duplicates", () => {
    expect(normalizeCapabilities([" Isolation ", "oxygen", "ISOLATION"])).toEqual([
      "isolation",
      "oxygen",
    ]);
  });

  it("returns the required tags a room does not support", () => {
    expect(
      missingCapabilities(["isolation", "oxygen"], ["oxygen", "accessible"]),
    ).toEqual(["isolation"]);
  });
});
