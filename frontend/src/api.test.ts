import { afterEach, describe, expect, it, vi } from "vitest";
import { api, login, setActiveDepartment, token } from "./api";

afterEach(() => vi.unstubAllGlobals());
describe("secure-session failures", () => {
  it("distinguishes an unavailable backend from invalid credentials", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("Bad gateway", { status: 502 })),
    );
    await expect(login("admin", "example")).rejects.toMatchObject({
      code: "SESSION_UNAVAILABLE",
      status: 502,
    });
  });
  it("reports a network failure without exposing a raw fetch error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new TypeError("Failed to fetch")),
    );
    await expect(token()).rejects.toMatchObject({
      code: "SERVICE_UNAVAILABLE",
    });
  });
  it("rejects a successful response without a CSRF token", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({})));
    await expect(token()).rejects.toMatchObject({ code: "INVALID_SESSION" });
  });
  it("keeps authentication failures distinct from service failures", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(
          Response.json({ token: "test", headerName: "X-CSRF-TOKEN" }),
        )
        .mockResolvedValueOnce(new Response(null, { status: 401 })),
    );
    await expect(login("admin", "incorrect")).rejects.toThrow(
      "Invalid username or password.",
    );
  });
});
