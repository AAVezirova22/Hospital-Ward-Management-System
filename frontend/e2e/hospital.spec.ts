import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
const password = process.env.E2E_PASSWORD || "MedcoreDemo2026!";
async function signIn(page: Page, name = "admin") {
  await page.goto("/app");
  await page.getByLabel("Username", { exact: true }).fill(name);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Your ward. In the moment." }),
  ).toBeVisible();
}
async function selectFirst(page: Page, label: string) {
  const s = page.getByLabel(label, { exact: true });
  const value = await s.locator("option").nth(1).getAttribute("value");
  await s.selectOption(value!);
}
async function newPatient(page: Page) {
  await page.getByRole("link", { name: "Patients", exact: true }).click();
  await page.getByRole("button", { name: "New patient", exact: true }).click();
  const suffix = Date.now().toString().slice(-9);
  await page.getByLabel("Patient ID", { exact: true }).fill("E2E-" + suffix);
  await page.getByLabel("First name", { exact: true }).fill("E2E" + suffix);
  await page.getByLabel("Last name", { exact: true }).fill("Browser");
  await page.getByLabel("Date of birth", { exact: true }).fill("1985-04-12");
  await page
    .getByLabel("Phone number", { exact: true })
    .fill("+359 888 000 000");
  await page
    .getByLabel("Address", { exact: true })
    .fill("Synthetic test record");
  await page.getByRole("button", { name: "Save patient", exact: true }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByLabel("Search patients").fill("E2E-" + suffix);
  await page.getByRole("link", { name: "Open dossier" }).click();
  return suffix;
}
async function admitPatient(page: Page) {
  await page
    .getByRole("button", { name: "Admit patient", exact: true })
    .click();
  await selectFirst(page, "Attending / performing doctor");
  await selectFirst(page, "Destination room");
  await page.getByRole("button", { name: "Review details" }).click();
  await page
    .getByRole("button", { name: "Confirm admission", exact: true })
    .click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(
    page.getByText("CURRENT ADMISSION", { exact: true }),
  ).toBeVisible();
}
test("full patient journey: create, admit, procedure, transfer, discharge, reload, report", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await signIn(page);
  await newPatient(page);
  await admitPatient(page);
  await page
    .getByRole("button", { name: "Record procedure", exact: true })
    .click();
  await selectFirst(page, "Procedure");
  await page
    .getByLabel("Notes", { exact: true })
    .fill("Browser workflow verified.");
  await page.getByRole("button", { name: "Review details" }).click();
  await page
    .getByRole("button", { name: "Confirm procedure", exact: true })
    .click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(page.getByText("Browser workflow verified.")).toBeVisible();
  await page.getByRole("button", { name: "Transfer", exact: true }).click();
  await selectFirst(page, "Destination room");
  await page
    .getByLabel("Reason for transfer")
    .fill("Operational transfer test");
  await page.getByRole("button", { name: "Review details" }).click();
  await page
    .getByRole("button", { name: "Confirm transfer", exact: true })
    .click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(page.getByText("Operational transfer test")).toBeVisible();
  await page.getByRole("button", { name: "Discharge", exact: true }).click();
  await page
    .getByRole("button", { name: "Confirm discharge", exact: true })
    .click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(page.getByText("DISCHARGED", { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText("Browser workflow verified.")).toBeVisible();
  await page.getByRole("link", { name: "Reports", exact: true }).click();
  await expect(
    page
      .getByRole("cell", { name: "Complete blood count", exact: true })
      .first(),
  ).toBeVisible();
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export CSV" }).click();
  expect((await download).suggestedFilename()).toMatch(
    /^procedure-report-department-\d+\.csv$/,
  );
  expect(errors).toEqual([]);
});
test("catalogue creation and account administration", async ({ page }) => {
  await signIn(page);
  await page.getByRole("link", { name: "Room capacity", exact: true }).click();
  await page.getByRole("button", { name: "Add room" }).click();
  await page
    .getByLabel("Room number", { exact: true })
    .fill("E" + Date.now().toString().slice(-6));
  await page.getByLabel("Bed count").fill("3");
  await page.getByRole("button", { name: "Save room" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("link", { name: "Doctors", exact: true }).click();
  await page.getByRole("button", { name: "Add doctor" }).click();
  await page
    .getByLabel("Doctor ID", { exact: true })
    .fill("DOC-E" + Date.now().toString().slice(-6));
  await page.getByLabel("First name").fill("Test");
  await page.getByLabel("Last name").fill("Doctor");
  await page.getByLabel("Specialty").fill("Internal medicine");
  await page.getByRole("button", { name: "Save doctor" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("link", { name: "Procedures", exact: true }).click();
  await page.getByRole("button", { name: "Add procedure" }).click();
  await page
    .getByLabel("Procedure code")
    .fill("E" + Date.now().toString().slice(-6));
  await page.getByLabel("Procedure name").fill("Browser test procedure");
  await page.getByLabel("Cost (EUR)").fill("75.50");
  await page.getByRole("button", { name: "Save procedure" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("link", { name: "Team access" }).click();
  await page.getByRole("button", { name: "Add user" }).click();
  await page
    .getByLabel("Username", { exact: true })
    .fill("e2e" + Date.now().toString().slice(-6));
  await page
    .getByLabel("Password (12+ characters)")
    .fill("BrowserPassword123!");
  await page.getByRole("button", { name: "Save user" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("link", { name: "Audit history" }).click();
  await expect(
    page.getByRole("cell", { name: "USER_SAVED", exact: true }).first(),
  ).toBeVisible();
});
test("assistant search, discharge confirmation, keyboard and cancellation", async ({
  page,
}) => {
  await signIn(page);
  await newPatient(page);
  await admitPatient(page);
  await page.keyboard.press("Control+k");
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByLabel("Assistant message").fill("Rooms with two free beds");
  await page.getByRole("button", { name: "Send message" }).click();
  await expect(
    page.getByText(
      "Current capacity; availability is checked again before placement.",
    ),
  ).toBeVisible();
  await page.getByLabel("Assistant message").fill("discharge him");
  await page.getByRole("button", { name: "Send message" }).click();
  await expect(page.getByText("DISCHARGE PROPOSAL")).toBeVisible();
  await page.getByRole("button", { name: "Cancel proposal" }).click();
  await expect(page.getByText("CANCELLED", { exact: true })).toBeVisible();
  await page.getByLabel("Assistant message").fill("discharge him");
  await page.getByRole("button", { name: "Send message" }).click();
  await page
    .getByRole("button", { name: "Confirm discharge", exact: true })
    .click();
  await expect(page.getByText("EXECUTED", { exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(page.getByText("DISCHARGED", { exact: true })).toBeVisible();
});
test("doctor interface restricts writes and unauthorized navigation", async ({
  page,
}) => {
  await signIn(page, "doctor");
  await expect(
    page.getByRole("link", { name: "Team access" }),
  ).not.toBeVisible();
  await page.getByRole("link", { name: "Patients", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "New patient" }),
  ).not.toBeVisible();
  await page.goto("/app/users");
  await expect(
    page.getByRole("heading", { name: "Access restricted" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Return to overview" }).click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(
    page.getByRole("heading", { name: "Welcome back." }),
  ).toBeVisible();
});
test("standard report filters, capacity and census render", async ({
  page,
}) => {
  await signIn(page, "staff");
  for (const name of [
    "Admissions",
    "Room capacity",
    "Doctors",
    "Procedures",
    "Reports",
  ]) {
    await page.getByRole("link", { name, exact: true }).click();
    await expect(page.locator("main h1")).toBeVisible();
    await expect(page.locator("main").getByRole("alert")).not.toBeVisible();
  }
  await page.getByRole("button", { name: "Hospitalized patients" }).click();
  await expect(
    page.getByRole("columnheader", { name: /admitted/i }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Bed capacity", exact: true }).click();
  await expect(
    page.getByRole("columnheader", { name: /available/i }),
  ).toBeVisible();
});
test("desktop and mobile visuals, reduced motion, no overflow", async ({
  page,
}) => {
  await signIn(page);
  await expect(page.locator("main > .scene-content")).toHaveCSS("opacity", "1");
  await page.screenshot({
    path: "test-results/overview-desktop.png",
    fullPage: true,
  });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Your ward. In the moment." }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.getByRole("button", { name: "Open navigation" }).click();
  await page.getByRole("link", { name: "Patients", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "People at the center." }),
  ).toBeVisible();
  await expect(page.locator("main > .scene-content")).toHaveCSS("opacity", "1");
  await page.screenshot({
    path: "test-results/patients-mobile.png",
    fullPage: true,
  });
});
