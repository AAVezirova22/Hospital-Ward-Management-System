import { expect, test } from "@playwright/test";

test("registration shows associated feedback for every invalid field", async ({
  page,
}) => {
  await page.route("**/api/v1/health", (route) =>
    route.fulfill({ json: { status: "UP" } }),
  );
  await page.route("**/api/v1/auth/me", (route) =>
    route.fulfill({ status: 401, json: { message: "Not signed in." } }),
  );
  await page.route("**/api/v1/demo/status", (route) =>
    route.fulfill({ json: { enabled: false } }),
  );
  await page.route("**/api/v1/registration/hospitals", (route) =>
    route.fulfill({ json: [{ id: 1, name: "Central Hospital" }] }),
  );

  await page.goto("/app/dashboard");
  await page
    .getByRole("button", { name: "Create a patient account", exact: true })
    .click();

  const requestedRole = page.getByLabel("Account request");
  await requestedRole.evaluate((element) => {
    (element as HTMLSelectElement).add(
      new Option("Invalid request", "INVALID"),
    );
  });
  await requestedRole.selectOption("INVALID");
  await page.getByLabel("Email").fill("not-an-email");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();

  for (const label of [
    "First name",
    "Last name",
    "Date of birth",
    "Hospital",
    "Email",
    "Username",
    "Password",
    "Account request",
  ]) {
    const field = page.getByLabel(label);
    await expect(field).toHaveAttribute("aria-invalid", "true");
    const descriptionIds =
      (await field.getAttribute("aria-describedby"))?.split(/\s+/) ?? [];
    expect(descriptionIds).not.toHaveLength(0);
    for (const id of descriptionIds) {
      await expect(page.locator(`[id="${id}"]`)).toBeVisible();
    }
  }
});
