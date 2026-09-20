import { afterEach, describe, expect, it, vi } from "vitest";
import { api, bindAccount, login, logout, setActiveDepartment, token } from "./api";

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

describe("department scope", () => {
  afterEach(() => setActiveDepartment(null));
  it("sends the selected department on clinical requests", async () => {
    setActiveDepartment(12);
    const fetchMock = vi
      .fn()
      .mockResolvedValue(Response.json([{ id: 1 }], { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await api("/patients");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/patients",
      expect.objectContaining({
        headers: expect.objectContaining({ "X-Department-Id": "12" }),
      }),
    );
  });
  it("clears the remembered department on logout", async () => {
    setActiveDepartment(12);
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(
          Response.json({ token: "t", headerName: "X-CSRF-TOKEN" }),
        )
        .mockResolvedValueOnce(new Response(null, { status: 204 })),
    );
    await logout();
    const fetchMock = vi
      .fn()
      .mockResolvedValue(Response.json([], { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await api("/patients");
    expect(fetchMock.mock.calls[0][1].headers["X-Department-Id"]).toBeUndefined();
  });
  it("stores the selected department per account", async () => {
    bindAccount(1);
    setActiveDepartment(12);
    bindAccount(2);
    setActiveDepartment(34);
    bindAccount(1);
    const fetchMock = vi
      .fn()
      .mockResolvedValue(Response.json([], { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await api("/patients");
    expect(fetchMock.mock.calls[0][1].headers["X-Department-Id"]).toBe("12");
    bindAccount(null);
    setActiveDepartment(null);
  });
});
