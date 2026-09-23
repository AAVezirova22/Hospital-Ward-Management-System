import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";

test("reports keep department scope and render doctor workload for the selected period", async ({
  page,
  context,
}) => {
  const departments = new Map([
    ["1", "North Department"],
    ["2", "South Department"],
  ]);
  let sessionDepartmentId = "1";
  let exportedDepartmentId: string | undefined;
  let workloadRequest: URL | undefined;
  const user = {
    id: 7,
    username: "admin",
    role: "ADMIN",
    doctorId: null,
    patientId: null,
  };

  await context.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const requestedDepartmentId = request.headers()["x-department-id"];
    const departmentId = requestedDepartmentId || sessionDepartmentId;
    if (requestedDepartmentId && !departments.has(requestedDepartmentId)) {
      await route.fulfill({
        status: 403,
        json: {
          code: "DEPARTMENT_ACCESS_DENIED",
          message: "You do not have access to this department.",
        },
      });
      return;
    }
    if (requestedDepartmentId) sessionDepartmentId = requestedDepartmentId;

    if (path === "/api/v1/health") {
      await route.fulfill({ json: { status: "UP" } });
      return;
    }
    if (path === "/api/v1/auth/me") {
      await route.fulfill({ json: user });
      return;
    }
    if (path === "/api/v1/workspaces") {
      await route.fulfill({
        json: {
          activeDepartmentId: Number(departmentId),
          hospitals: [
            {
              id: 1,
              name: "Test Hospital",
              owner: true,
              hasJoinCode: false,
              departments: Array.from(departments, ([id, name]) => ({
                id: Number(id),
                name,
                role: "ADMIN",
                hasJoinCode: false,
              })),
            },
          ],
        },
      });
      return;
    }
    if (path === "/api/v1/reports/procedures.csv") {
      exportedDepartmentId = departmentId;
      await route.fulfill({
        status: 200,
        contentType: "text/csv; charset=utf-8",
        headers: {
          "Content-Disposition": `attachment; filename=procedure-report-department-${departmentId}.csv`,
        },
        body: `Department,Record\r\n${departmentId},42\r\n`,
      });
      return;
    }
    if (path === "/api/v1/reports/procedures") {
      await route.fulfill({
        json: {
          rows: [],
          totalCost: 0,
          byDoctor: {},
          from: "2026-01-01",
          to: "2026-01-31",
        },
      });
      return;
    }
    if (path === "/api/v1/reports/doctor-workload") {
      workloadRequest = new URL(request.url());
      const query = workloadRequest.searchParams;
      await route.fulfill({
        json: {
          rows: [
            {
              doctor: {
                id: 101,
                version: 0,
                doctorIdentifier: "DR-101",
                firstName: "Ada",
                lastName: "Lovelace",
                specialty: "Cardiology",
                active: true,
              },
              activeAdmissions: 0,
              assignedBeds: 0,
              recentProcedures: 0,
            },
          ],
          from: query.get("from"),
          to: query.get("to"),
          timeZone: "UTC",
          scope: "Department",
        },
      });
      return;
    }
    if (
      ["/api/v1/patients", "/api/v1/doctors", "/api/v1/rooms"].includes(path)
    ) {
      const query = new URL(request.url()).searchParams;
      const page = Number(query.get("page") ?? "0");
      const size = Number(query.get("size") ?? "20");
      await route.fulfill({
        json: {
          items: [],
          page,
          size,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          nextPage: null,
        },
      });
      return;
    }
    if (path === "/api/v1/demo/status") {
      await route.fulfill({ json: { enabled: false } });
      return;
    }
    await route.fulfill({ json: [] });
  });

  await page.goto("/app/reports");
  await expect(
    page.getByRole("heading", { name: "Decisions, grounded in data." }),
  ).toBeVisible({ timeout: 30000 });
  await expect(page.locator(".workspace-switcher small").first()).toHaveText(
    "North Department",
  );
  const firstDepartmentId = await page.evaluate(() => {
    const key = Object.keys(localStorage).find((item) =>
      item.startsWith("medcore-department:"),
    );
    return key ? localStorage.getItem(key) : null;
  });
  expect(firstDepartmentId).toBe("1");

  const secondTab = await context.newPage();
  await secondTab.goto("/app/reports");
  await expect(
    secondTab.locator(".workspace-switcher small").first(),
  ).toHaveText("North Department");
  await secondTab
    .getByRole("button", { name: "Open hospital switcher" })
    .click();
  await secondTab
    .locator(".workspace-list button")
    .filter({ hasText: "South Department" })
    .first()
    .click();
  await expect(
    secondTab.locator(".workspace-switcher small").first(),
  ).toHaveText("South Department");
  await expect(page.locator(".workspace-switcher small").first()).toHaveText(
    "North Department",
  );
  expect(sessionDepartmentId).toBe("2");

  const csvRequest = page.waitForRequest((request) =>
    request.url().includes("/api/v1/reports/procedures.csv"),
  );
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export CSV" }).click();
  const [request, file] = await Promise.all([csvRequest, download]);
  expect(request.headers()["x-department-id"]).toBe(firstDepartmentId);
  expect(exportedDepartmentId).toBe(firstDepartmentId);
  expect(file.suggestedFilename()).toBe(
    `procedure-report-department-${firstDepartmentId}.csv`,
  );
  expect(await readFile((await file.path())!, "utf8")).toContain("1,42");

  await page.goto(
    "/app/reports?mode=doctor-workload&from=2026-01-01&to=2026-01-31",
  );
  await expect(
    page.getByRole("button", { name: "Doctor workload" }),
  ).toHaveAttribute("aria-pressed", "true");
  await expect(
    page.getByRole("heading", { name: "Doctor workload", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Scope: Department.")).toBeVisible();
  const workloadRow = page.getByRole("row", { name: /Ada Lovelace/ });
  await expect(workloadRow).toBeVisible();
  await expect(workloadRow.getByRole("cell")).toHaveText([
    "Cardiology",
    "0",
    "0",
    "0",
  ]);
  expect(workloadRequest?.searchParams.get("from")).toBe("2026-01-01");
  expect(workloadRequest?.searchParams.get("to")).toBe("2026-01-31");
  expect(workloadRequest?.searchParams.has("doctorId")).toBe(false);
});
