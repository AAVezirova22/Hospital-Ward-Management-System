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
  let utilizationRequest: URL | undefined;
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
    if (path === "/api/v1/reports/room-utilization") {
      utilizationRequest = new URL(request.url());
      const query = utilizationRequest.searchParams;
      const weekly = query.get("bucket") === "week";
      await route.fulfill({
        json: {
          rows: [
            {
              periodStart: query.get("from"),
              periodEnd: weekly ? "2026-01-04" : query.get("from"),
              roomId: 77,
              roomNumber: "307",
              bedCount: 2,
              occupiedBedHours: weekly ? 24 : 12,
              capacityBedHours: weekly ? 192 : 48,
              utilizationPercent: weekly ? 12.5 : 25,
            },
          ],
          from: query.get("from"),
          to: query.get("to"),
          bucket: query.get("bucket"),
          timeZone: "UTC",
          scope: "Department",
          capacityBasis:
            "Current configured bed count applied from each room's creation time",
        },
      });
      return;
    }
    if (path === "/api/v1/patients/88") {
      await route.fulfill({
        json: {
          patient: {
            id: 88,
            version: 0,
            patientIdentifier: "PT-88",
            firstName: "Grace",
            lastName: "Hopper",
            dateOfBirth: "1906-12-09",
            address: "",
            phoneNumber: "",
          },
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
          items:
            path === "/api/v1/rooms"
              ? [{ id: 77, roomNumber: "307", bedCount: 2 }]
              : [],
          page,
          size,
          totalElements: path === "/api/v1/rooms" ? 1 : 0,
          totalPages: path === "/api/v1/rooms" ? 1 : 0,
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

  await page.goto(
    "/app/reports?mode=room-utilization&from=2026-01-01&to=2026-01-31&bucket=week&roomId=77&patientId=88",
  );
  await expect(
    page.getByRole("button", { name: "Room utilization" }),
  ).toHaveAttribute("aria-pressed", "true");
  await expect(
    page.getByRole("heading", { name: "Utilization by room and period" }),
  ).toBeVisible();
  await expect(page.getByText("Historical room utilization")).toBeVisible();
  await expect(page.getByText("Scope: Department")).toBeVisible();
  await expect(
    page.getByText(/compared with each room's full configured capacity/i),
  ).toBeVisible();
  await expect(
    page.getByText(/Current configured bed count applied from each room's creation time/),
  ).toBeVisible();
  await expect(page.getByRole("combobox", { name: "Time interval" })).toHaveValue(
    "week",
  );
  await expect(page.getByRole("combobox", { name: "Room" })).toHaveValue("77");
  const utilizationRow = page.getByRole("row", { name: /307/ });
  await expect(utilizationRow).toBeVisible();
  await expect(utilizationRow.getByRole("cell")).toHaveText([
    "2026-01-01 – 2026-01-04",
    "2",
    "24.0",
    "192.0",
    "12.5%",
  ]);
  expect(utilizationRequest?.searchParams.get("from")).toBe("2026-01-01");
  expect(utilizationRequest?.searchParams.get("to")).toBe("2026-01-31");
  expect(utilizationRequest?.searchParams.get("bucket")).toBe("week");
  expect(utilizationRequest?.searchParams.get("roomId")).toBe("77");
  expect(utilizationRequest?.searchParams.get("patientId")).toBe("88");
  const dailyRequest = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return (
      url.pathname === "/api/v1/reports/room-utilization" &&
      url.searchParams.get("bucket") === "day"
    );
  });
  await page.getByRole("combobox", { name: "Time interval" }).selectOption("day");
  expect(new URL((await dailyRequest).url()).searchParams.get("bucket")).toBe(
    "day",
  );
  await expect(utilizationRow.getByRole("cell")).toHaveText([
    "2026-01-01",
    "2",
    "12.0",
    "48.0",
    "25.0%",
  ]);
});
