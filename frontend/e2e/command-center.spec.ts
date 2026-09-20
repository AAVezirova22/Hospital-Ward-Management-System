import { test, expect } from "@playwright/test";

async function enter(
  page: import("@playwright/test").Page,
  role = "administrator",
) {
  await page.goto("/app/dashboard");
  await page
    .getByRole("button", {
      name:
        role === "administrator"
          ? "Enter demo as administrator"
          : "View as doctor",
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("heading", { name: "Your ward. In the moment." }),
  ).toBeVisible();
}
test("command center exposes capacity, patient drawer, timeline and live stream", async ({
  page,
}) => {
  await enter(page);
  await expect(
    page.getByText("Live updates connected", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Admissions today", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Nurses’ station", { exact: true }),
  ).toBeVisible();
  await page
    .locator(".overview-floor-plan .ward-patient:not(.vacant):not(.restricted)")
    .first()
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("link", { name: "View patient timeline" }).click();
  await expect(
    page.getByRole("heading", { name: "Care journey" }),
  ).toBeVisible();
  await expect(page.locator(".care-event").first()).toBeVisible();
});
test("mobile navigation and quick actions stay usable without overflow", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await enter(page);
  await expect(page.locator(".compact-capacity")).toBeVisible();
  await expect(page.locator(".overview-floor-plan")).toBeHidden();
  await page.getByRole("button", { name: "Open quick actions" }).click();
  await page
    .locator(".quick-action-menu")
    .getByRole("button", { name: "Transfer patient" })
    .click();
  await expect(
    page.getByRole("dialog", { name: "Choose a patient" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Close dialog", exact: true }).click();
  await page
    .getByRole("navigation", { name: "Quick navigation" })
    .getByRole("link", { name: "Rooms" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Make room for what’s next." }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
test("doctor capacity map preserves role restrictions", async ({ page }) => {
  await enter(page, "doctor");
  await page
    .locator(".overview-floor-plan .ward-patient:not(.vacant):not(.restricted)")
    .first()
    .click();
  await expect(
    page.getByRole("button", { name: "Prepare transfer" }),
  ).toHaveCount(0);
  expect((await page.request.get("/api/v1/management/health")).status()).toBe(
    403,
  );
});
test("evening rehearsal is local and can be paused and closed", async ({
  page,
}) => {
  await enter(page);
  let writes = 0;
  page.on("request", (r) => {
    if (r.method() === "POST") writes++;
  });
  await page
    .getByRole("button", { name: "Run busy evening simulation" })
    .click();
  await expect(
    page.getByText("Synthetic patient arrived", { exact: true }),
  ).toBeVisible({ timeout: 8000 });
  await page.getByRole("button", { name: "Pause", exact: true }).click();
  await expect(
    page.getByText("Rehearsal paused", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Close simulation" }).click();
  await expect(
    page.getByText("Synthetic patient arrived", { exact: true }),
  ).toHaveCount(0);
  expect(writes).toBe(0);
});
