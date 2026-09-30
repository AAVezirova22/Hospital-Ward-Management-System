import { expect, test } from "@playwright/test";

test("patient drafts survive catalogue refreshes and animated navigation", async ({
  page,
}) => {
  const errors: string[] = [];
  const styleViolations: string[] = [];
  let roomRequests = 0;
  let patientWrites = 0;
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (
      message.text().includes("violates") &&
      message.text().includes("style-src")
    )
      styleViolations.push(message.text());
  });
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.addInitScript(() => {
    localStorage.setItem("medcore-department:9865", "1");
  });
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace("/api/v1", "");
    if (path === "/operations/stream") return route.abort();
    if (path === "/health") return route.fulfill({ json: { status: "UP" } });
    if (path === "/auth/me")
      return route.fulfill({
        json: { id: 9865, username: "admin", role: "ADMIN", doctorId: null },
      });
    if (path === "/workspaces")
      return route.fulfill({
        json: {
          activeDepartmentId: 1,
          hospitals: [
            {
              id: 1,
              name: "Test Hospital",
              owner: true,
              departments: [{ id: 1, name: "Test Ward", role: "ADMIN" }],
            },
          ],
        },
      });
    if (path === "/reports/operations")
      return route.fulfill({
        json: { expectedDischargesToday: 0, timeZone: "Europe/Sofia" },
      });
    if (["/rooms", "/doctors", "/patients"].includes(path)) {
      if (path === "/patients" && request.method() !== "GET") patientWrites++;
      if (path === "/rooms") roomRequests++;
      const pageIndex = Number(url.searchParams.get("page") ?? 0);
      const room = {
        id: pageIndex + 1,
        roomNumber: pageIndex ? "202" : "101",
        active: true,
        bedCount: 4,
        occupiedBeds: pageIndex ? 0 : 4,
        availableBeds: pageIndex ? 4 : 0,
        heldBeds: 0,
        capabilities: [],
        holds: [],
      };
      const doctor = {
        id: pageIndex + 1,
        firstName: pageIndex ? "Grace" : "Ada",
        lastName: "Doctor",
        active: true,
      };
      const items =
        path === "/rooms" ? [room] : path === "/doctors" ? [doctor] : [];
      return route.fulfill({
        json: {
          items,
          page: pageIndex,
          size: 1,
          totalElements: path === "/patients" ? 0 : 2,
          totalPages: path === "/patients" ? 0 : 2,
          hasNext: path !== "/patients" && pageIndex === 0,
          nextPage: path !== "/patients" && pageIndex === 0 ? 1 : null,
        },
      });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto("/app/admissions");
  await expect(
    page.getByRole("heading", { name: "Every stay, connected." }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Patients", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "New patient", exact: true }),
  ).toHaveCount(1);
  await expect(
    page.getByRole("button", { name: "New patient", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "New patient", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Patient ID", { exact: true }).fill("DRAFT-REFRESH");
  await dialog.getByLabel("First name", { exact: true }).fill("Synthetic");
  await dialog.getByLabel("Last name", { exact: true }).fill("Draft");
  const before = roomRequests;
  await expect.poll(() => roomRequests, { timeout: 35_000 }).toBeGreaterThan(before);
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel("Patient ID", { exact: true })).toHaveValue(
    "DRAFT-REFRESH",
  );
  await expect(dialog.getByLabel("First name", { exact: true })).toHaveValue(
    "Synthetic",
  );
  await expect(dialog.getByLabel("Last name", { exact: true })).toHaveValue(
    "Draft",
  );
  await dialog.getByLabel("Date of birth", { exact: true }).fill("1985-04-12");
  await dialog.getByRole("button", { name: "Close dialog" }).click();
  await expect(
    page.getByLabel("Assigned doctor").locator("option"),
  ).toHaveCount(3);
  await expect(page.getByLabel("Current room").locator("option")).toHaveCount(
    3,
  );
  await page.getByRole("button", { name: /^Notifications/ }).click();
  await expect(page.getByRole("dialog")).toContainText(
    "Room 101 is at full capacity",
  );
  await expect(page.getByRole("dialog")).toContainText("4 beds available");
  await page.getByRole("button", { name: "Close dialog" }).click();
  await page.getByRole("link", { name: "Admissions", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Every stay, connected." }),
  ).toHaveCount(1);
  await expect(
    page.getByRole("heading", { name: "Every stay, connected." }),
  ).toBeVisible();
  await expect(
    page.getByLabel("Attending doctor").locator("option"),
  ).toHaveCount(3);
  await page.getByRole("link", { name: "Patients", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "New patient", exact: true }),
  ).toHaveCount(1);
  await expect(
    page.getByRole("button", { name: "New patient", exact: true }),
  ).toBeVisible();
  expect(errors).toEqual([]);
  expect(styleViolations).toEqual([]);
  expect(patientWrites).toBe(0);
});
