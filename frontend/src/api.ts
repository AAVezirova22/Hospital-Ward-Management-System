import type { PageResult } from "./api/contracts";

// These pages still consume several endpoint shapes that are not modeled yet.
// Keep the shared legacy row type permissive; typed endpoints use api/contracts.
import type { PageResult } from "./api/contracts";

export type Row = any;
export type User = {
  id: number;
  username: string;
  role: "ADMIN" | "MEDICAL_STAFF" | "DOCTOR" | "PATIENT";
  accountRole?: "ADMIN" | "MEDICAL_STAFF" | "DOCTOR" | "PATIENT";
  departmentRole?: "ADMIN" | "MEDICAL_STAFF" | "DOCTOR" | "PATIENT";
  doctorId: number | null;
  patientId?: number | null;
  requestedRole?: string | null;
  emailVerified?: boolean;
};
export function patientHref(p: {
  patientIdentifier?: string | null;
  id?: number | string | null;
}) {
  return (
    "/app/patients/" +
    encodeURIComponent(String(p.patientIdentifier || p.id || ""))
  );
}
let csrf: { token: string; headerName: string } | null = null;
let departmentId: string | null = null;
let accountId: string | null = null;
const DEPARTMENT_KEY = "medcore-department";

function departmentStorageKey() {
  return accountId ? `${DEPARTMENT_KEY}:${accountId}` : DEPARTMENT_KEY;
}

export function bindAccount(id: string | number | null) {
  accountId = id == null ? null : String(id);
  departmentId = null;
}

export function activeDepartment() {
  if (!accountId) return departmentId;
  if (departmentId != null) return departmentId;
  try {
    departmentId = localStorage.getItem(departmentStorageKey());
  } catch {}
  return departmentId;
}

export function setActiveDepartment(id: string | number | null) {
  departmentId = id == null ? null : String(id);
  try {
    if (departmentId)
      localStorage.setItem(departmentStorageKey(), departmentId);
    else localStorage.removeItem(departmentStorageKey());
  } catch {}
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event("workspace-changed"));
  }
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
export async function token() {
  let r: Response;
  try {
    r = await fetch("/api/v1/auth/csrf", {
      credentials: "include",
      cache: "no-store",
    });
  } catch {
    throw new ApiError(
      0,
      "SERVICE_UNAVAILABLE",
      "Cannot reach the department service. Check your connection and try signing in again.",
    );
  }
  if (!r.ok)
    throw new ApiError(
      r.status,
      "SESSION_UNAVAILABLE",
      r.status >= 500
        ? "The department service is temporarily unavailable. Please try signing in again shortly."
        : "Your secure session could not be started. Refresh the page and try again.",
    );
  const result = await r.json().catch(() => null);
  if (!result?.token || !result?.headerName)
    throw new ApiError(
      502,
      "INVALID_SESSION",
      "The department service returned an invalid session. Please try again shortly.",
    );
  csrf = result;
  return csrf!;
}
export async function api<T = any>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = {};
  const multipart = typeof FormData !== "undefined" && body instanceof FormData;
  const department = activeDepartment();
  if (department) headers["X-Department-Id"] = department;
  if (method !== "GET") {
    const t = csrf || (await token());
    headers[t.headerName] = t.token;
    if (!multipart) headers["Content-Type"] = "application/json";
  }
  const r = await fetch("/api/v1" + path, {
    method,
    headers,
    credentials: "include",
    body:
      body === undefined
        ? undefined
        : multipart
          ? (body as FormData)
          : JSON.stringify(body),
  });
  if (!r.ok) {
    const e = await r.json().catch(() => ({}));
    if (r.status === 401) {
      csrf = null;
      window.dispatchEvent(new Event("session-expired"));
    }
    if (
      r.status === 403 &&
      e.code === "DEPARTMENT_ACCESS_DENIED" &&
      department &&
      !path.startsWith("/assistant/") &&
      !path.startsWith("/ai-actions/")
    ) {
      setActiveDepartment(null);
      return api(path, method, body);
    }
    throw new ApiError(
      r.status,
      e.code || "REQUEST_FAILED",
      e.message || "The request could not be completed.",
    );
  }
  const result =
    r.status === 204 || r.headers.get("content-length") === "0"
      ? (undefined as T)
      : await r.text().then((t) => (t ? JSON.parse(t) : undefined));
  if (
    method !== "GET" &&
    !path.includes("assistant") &&
    !path.includes("logout")
  ) {
    const message = path.endsWith("/discharge")
      ? "Admission closed · Bed released"
      : path.endsWith("/transfer")
        ? "Transfer complete · Capacity updated"
        : path === "/admissions"
          ? "Admission active"
          : path.endsWith("/confirm")
            ? "Confirmed action completed"
            : path.endsWith("/cancel")
              ? "Proposal cancelled"
              : path === "/workspaces/join"
                ? "Workspace joined"
                : path === "/workspaces/hospitals"
                  ? "Hospital created"
                  : path.includes("/workspaces/hospitals/") &&
                      path.endsWith("/departments")
                    ? "Department created"
                    : path.endsWith("/code")
                      ? "Join code replaced"
                      : "Changes saved";
    window.dispatchEvent(new CustomEvent("saved", { detail: message }));
  }
  return result;
}
export async function allPages<T>(path: string): Promise<T[]> {
const separator = path.indexOf("?");
const endpoint = separator < 0 ? path : path.slice(0, separator);
const params = new URLSearchParams(
  separator < 0 ? "" : path.slice(separator + 1),
);
params.set("page", "0");
params.set("size", "100");

const rows: T[] = [];
const visited = new Set<number>();
let page = 0;

while (!visited.has(page)) {
  visited.add(page);
  params.set("page", String(page));

  const result = await api<PageResult<T>>(
    `${endpoint}?${params.toString()}`,
  );

  if (!result || !Array.isArray(result.items))
    throw new ApiError(
      502,
      "INVALID_PAGINATION",
      "The department service returned an invalid page.",
    );

  rows.push(...result.items);

  if (!result.hasNext) return rows;

  const nextPage = result.nextPage ?? result.page + 1;

  if (!Number.isInteger(nextPage) || nextPage <= page)
    throw new ApiError(
      502,
      "INVALID_PAGINATION",
      "The department service returned invalid page navigation.",
    );

  page = nextPage;
}

throw new ApiError(
  502,
  "INVALID_PAGINATION",
  "The department service returned a repeated page.",
);
}

export async function downloadFile(
  path: string,
  department: string | number,
): Promise<{ blob: Blob; filename: string }> {
  const departmentId = String(department);

  const r = await fetch("/api/v1" + path, {
    headers: { "X-Department-Id": departmentId },
    credentials: "include",
    cache: "no-store",
  });

  if (!r.ok) {
    const e = await r.json().catch(() => ({}));

    if (r.status === 401) {
      csrf = null;
      window.dispatchEvent(new Event("session-expired"));
    }

    throw new ApiError(
      r.status,
      e.code || "REQUEST_FAILED",
      e.message || "The download could not be completed.",
    );
  }

  const disposition = r.headers.get("content-disposition") || "";
  const encodedFilename =
    disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const plainFilename =
    disposition.match(/filename="?([^";]+)"?/i)?.[1];

  const filename = encodedFilename
    ? decodeURIComponent(encodedFilename)
    : plainFilename || "download";

  return { blob: await r.blob(), filename };
}
}
export async function login(username: string, password: string): Promise<User> {
  const t = await token();
  const r = await fetch("/api/v1/auth/login", {
    method: "POST",
    credentials: "include",
    headers: {
      [t.headerName]: t.token,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({ username, password }),
  });
  if (!r.ok)
    throw new ApiError(
      r.status,
      "LOGIN_FAILED",
      r.status === 401
        ? "Invalid username or password."
        : r.status === 403
          ? "Your sign-in session expired. Please try signing in again."
          : "The department service is temporarily unavailable. Please try again shortly.",
    );
  const user = await r.json();
  csrf = null;
  await token();
  bindAccount(user.id);
  try {
    return await api<User>("/auth/me");
  } catch {
    return user;
  }
}
export async function logout() {
  try {
    await api("/auth/logout", "POST");
  } finally {
    csrf = null;
    setActiveDepartment(null);
    bindAccount(null);
    try {
      localStorage.removeItem("medcore-department");
    } catch {}
  }
}
export const fullName = (
  p: { firstName?: string; lastName?: string } | null | undefined,
) => (p ? `${p.firstName} ${p.lastName}` : "Not recorded");
export const money = (n: number) =>
  new Intl.NumberFormat("en", { style: "currency", currency: "EUR" }).format(
    n || 0,
  );
export const date = (v: string) =>
  v
    ? new Date(v).toLocaleString("en-GB", {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "Not recorded";
