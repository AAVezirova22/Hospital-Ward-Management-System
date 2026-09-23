import { expect, test } from "@playwright/test";

test("demo role entry authenticates through the same-origin API proxy", async ({
  page,
}) => {
  await page.goto("/app/dashboard");

  const doctorEntry = page.getByRole("button", {
    name: "View as doctor",
    exact: true,
  });
  await expect(doctorEntry).toBeVisible({ timeout: 50_000 });
  await doctorEntry.click();

  await expect(
    page.getByRole("button", { name: "Sign out", exact: true }),
  ).toBeVisible();
  const response = await page.request.get("/api/v1/auth/me");
  expect(response.ok()).toBeTruthy();
  expect((await response.json()).role).toBe("DOCTOR");
});
