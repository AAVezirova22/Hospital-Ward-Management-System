import { expect, test } from "@playwright/test";

test("seeded ward planner stages without saving and confirms through the authorized service", async ({
  page,
}) => {
  await page.goto("/app/dashboard");
  await page
    .getByRole("button", { name: "Enter demo as administrator", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "The ward, at a glance." }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Reset demonstration", exact: true })
    .click();
  await page.getByLabel("Type RESET DEMO").fill("RESET DEMO");
  await page.getByRole("button", { name: "Restore demo", exact: true }).click();
  await page
    .getByRole("button", { name: "Enter demo as administrator", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "The ward, at a glance." }),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/demo-overview.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.goto("/app/planner");
  const admissions = await (
    await page.request.get("/api/v1/admissions")
  ).json();
  const rooms = await (await page.request.get("/api/v1/rooms")).json();
  const view = admissions.find((v: any) => v.admission.status === "ACTIVE");
  const destination = rooms.find(
    (r: any) =>
      r.active && r.availableBeds > 0 && r.id !== view.assignment.roomId,
  );
  await page
    .getByLabel("Patient", { exact: true })
    .selectOption(String(view.admission.id));
  await page
    .getByLabel("Destination", { exact: true })
    .selectOption(String(destination.id));
  await page
    .getByRole("button", { name: "Preview transfer", exact: true })
    .click();
  const staged = await (
    await page.request.get(`/api/v1/admissions/${view.admission.id}`)
  ).json();
  expect(staged.assignment.roomId).toBe(view.assignment.roomId);
  await page.getByRole("button", { name: "Review plan", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Review your plan" }),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/demo-planner-review.png",
    fullPage: true,
    animations: "disabled",
  });
  await page
    .getByRole("button", { name: "Confirm transfers", exact: true })
    .click();
  await expect(
    page.getByRole("status").filter({ hasText: "1 transfer completed" }),
  ).toBeVisible();
  const saved = await (
    await page.request.get(`/api/v1/admissions/${view.admission.id}`)
  ).json();
  expect(saved.assignment.roomId).toBe(destination.id);
  expect(saved.admission.version).toBeGreaterThan(view.admission.version);
  await page.getByLabel("Additional arrivals").fill("5");
  await page
    .getByRole("button", { name: "Simulate arrivals", exact: true })
    .click();
  await expect(page.getByText(/placements available/)).toBeVisible();
  await page.goto("/app/presentation");
  await expect(
    page.getByRole("img", { name: "QR code linking to this workspace" }),
  ).toBeVisible();
  await expect(page.locator(".sidebar")).toBeHidden();
});

test("phone command access and doctor restrictions remain usable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/app/dashboard");
  await page
    .getByRole("button", { name: "View as doctor", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Ask operations assistant", exact: true })
    .click();
  await page.getByLabel("Assistant message").fill("Show department status");
  await page.getByRole("button", { name: "Send message", exact: true }).click();
  await expect(page.locator(".ai-result")).toBeVisible();
  await page
    .getByRole("button", { name: "Close assistant", exact: true })
    .click();
  await page.goto("/app/planner");
  await expect(
    page.getByText("Your doctor role can view assigned patients", {
      exact: false,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Preview transfer", exact: true }),
  ).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: "test-results/demo-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
});

test("registration explains verification and doctor review before submitting", async ({
  page,
}) => {
  await page.goto("/app/dashboard");
  await page
    .getByRole("button", { name: "Create a patient account", exact: true })
    .click();
  await page.getByLabel("Account request").selectOption("DOCTOR");
  await expect(
    page.getByText("Confirm your email first.", { exact: false }),
  ).toBeVisible();
  await page.getByLabel("First name", { exact: true }).fill("Maya");
  await page.getByLabel("Last name", { exact: true }).fill("Koleva");
  await page.getByLabel("Date of birth", { exact: true }).fill("1994-03-12");
  await page.getByLabel("Email", { exact: true }).fill("maya@example.test");
  await page.getByLabel("Username", { exact: true }).fill("maya_demo");
  await page
    .getByLabel("Password", { exact: true })
    .fill("RegistrationPassword123!");
  await page.route("**/api/v1/registration/signup", (route) =>
    route.fulfill({
      json: { message: "Check your inbox to confirm your email." },
    }),
  );
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Check your inbox", exact: true }),
  ).toBeVisible();
});
