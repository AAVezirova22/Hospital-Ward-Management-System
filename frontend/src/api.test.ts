import { afterEach, describe, expect, it, vi } from "vitest";
import {
  activeDepartment,
  api,
  bindAccount,
  downloadFile,
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
  it("retries workspace listing without a stale selected department", async () => {
    setActiveDepartment(12);
    const workspaces = { activeDepartmentId: -1, hospitals: [] };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        Response.json(
          { code: "DEPARTMENT_ACCESS_DENIED", message: "Denied" },
          { status: 403 },
        ),
      )
      .mockResolvedValueOnce(Response.json(workspaces));
    vi.stubGlobal("fetch", fetchMock);

    await expect(api("/workspaces")).resolves.toEqual(workspaces);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][1].headers["X-Department-Id"]).toBe("12");
    expect(fetchMock.mock.calls[1][1].headers["X-Department-Id"]).toBeUndefined();
    expect(activeDepartment()).toBeNull();
  it("downloads a file using the department captured by its caller", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("Department,Record\r\n12,34\r\n", {
        headers: {
          "Content-Disposition":
            "attachment; filename=procedure-report-department-12.csv",
        },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const file = await downloadFile(
      "/reports/procedures.csv?from=2026-01-01",
      12,
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/reports/procedures.csv?from=2026-01-01",
      expect.objectContaining({
        headers: { "X-Department-Id": "12" },
        credentials: "include",
        cache: "no-store",
      }),
    );
    expect(file.filename).toBe("procedure-report-department-12.csv");
    expect(await file.blob.text()).toContain("12,34");
  });
  it("surfaces a server rejection for an unauthorized download scope", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      Response.json(
        {
          code: "DEPARTMENT_ACCESS_DENIED",
          message: "You do not have access to this department.",
        },
        { status: 403 },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);
    await expect(
      downloadFile("/reports/procedures.csv", 999),
    ).rejects.toMatchObject({
      code: "DEPARTMENT_ACCESS_DENIED",
      status: 403,
    });
    expect(fetchMock.mock.calls[0][1].headers["X-Department-Id"]).toBe("999");
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
