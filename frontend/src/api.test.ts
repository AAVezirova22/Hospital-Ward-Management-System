import { afterEach, describe, expect, it, vi } from "vitest";
import {
  allPages,
  api,
  bindAccount,
  login,
  logout,
  setActiveDepartment,
  token,
} from "./api";

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
  it("does not retry assistant writes in another department after access is denied", async () => {
    setActiveDepartment(12);
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        Response.json(
          { code: "DEPARTMENT_ACCESS_DENIED", message: "Denied" },
          { status: 403 },
        ),
      );
    vi.stubGlobal("fetch", fetchMock);
    await expect(
      api("/assistant/messages", "POST", { message: "Import files" }),
    ).rejects.toMatchObject({ code: "DEPARTMENT_ACCESS_DENIED" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1].headers["X-Department-Id"]).toBe("12");
  });
  it("preserves multipart bodies while including session and CSRF headers", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        Response.json({ token: "upload-csrf", headerName: "X-CSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(Response.json({ id: "source-id" }));
    vi.stubGlobal("fetch", fetchMock);
    await token();
    const form = new FormData();
    form.append("file", new Blob(["example"]), "notes.txt");
    await api("/assistant/sources", "POST", form);
    expect(fetchMock.mock.calls[1][1].body).toBe(form);
    expect(fetchMock.mock.calls[1][1].headers).toEqual({
      "X-CSRF-TOKEN": "upload-csrf",
    });
    expect(fetchMock.mock.calls[1][1].credentials).toBe("include");
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
    expect(
      fetchMock.mock.calls[0][1].headers["X-Department-Id"],
    ).toBeUndefined();
  });
  it("stores the selected department per account", async () => {
    const store: Record<string, string> = {};
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => store[key] ?? null,
      setItem: (key: string, value: string) => {
        store[key] = value;
      },
      removeItem: (key: string) => {
        delete store[key];
      },
    });
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

describe("paged API responses", () => {
  afterEach(() => setActiveDepartment(null));
  it("loads every bounded page while preserving request filters", async () => {
    setActiveDepartment(null);
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        Response.json({
          items: [{ id: 1 }],
          page: 0,
          size: 100,
          totalElements: 2,
          totalPages: 2,
          hasNext: true,
          nextPage: 1,
        }),
      )
      .mockResolvedValueOnce(
        Response.json({
          items: [{ id: 2 }],
          page: 1,
          size: 100,
          totalElements: 2,
          totalPages: 2,
          hasNext: false,
          nextPage: null,
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    await expect(allPages<{ id: number }>("/admissions?status=ACTIVE")).resolves.toEqual([
      { id: 1 },
      { id: 2 },
    ]);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/admissions?status=ACTIVE&size=100&page=0",
      "/api/v1/admissions?status=ACTIVE&size=100&page=1",
    ]);
  });
});
