import { expect, test } from "@playwright/test";

test("workspace switcher does not persist the no-department sentinel", async ({
  page,
}) => {
  const workspaceHeaders: (string | undefined)[] = [];
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === "/api/v1/health") {
      await route.fulfill({ json: { status: "UP" } });
    } else if (path === "/api/v1/auth/me") {
      await route.fulfill({
        json: {
          id: 9865,
          username: "stale-scope-user",
          role: "ADMIN",
          accountRole: "ADMIN",
          departmentRole: "MEDICAL_STAFF",
          doctorId: null,
          patientId: null,
          requestedRole: null,
          emailVerified: true,
        },
      });
    } else if (path === "/api/v1/workspaces") {
      workspaceHeaders.push(
        route.request().headers()["x-department-id"],
      );
      await route.fulfill({
        json: { activeDepartmentId: -1, hospitals: [] },
      });
    } else if (path === "/api/v1/audit") {
      await route.fulfill({
        json: { page: 0, size: 50, total: 0, events: [] },
      });
    } else {
      await route.fulfill({ json: [] });
    }
  });

  await page.goto("/app/audit");
  await expect(
    page.locator(".workspace-switcher small").first(),
  ).toHaveText("Choose a department");
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem("medcore-department:9865")))
    .toBeNull();
  expect(workspaceHeaders.length).toBeGreaterThan(0);
  expect(workspaceHeaders.every((header) => header === undefined)).toBe(true);
});
