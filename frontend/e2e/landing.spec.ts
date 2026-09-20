import { expect, test } from "@playwright/test";

test("product landing opens the assistant and a walkthrough request", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "The ward, in the moment." }),
  ).toBeVisible();
  await page.getByRole("link", { name: "See the assistant" }).click();
  await expect(
    page.getByRole("heading", {
      name: "Ask the ward. Confirm before anything writes.",
    }),
  ).toBeVisible();
  await page.locator("[data-film-book]").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Request a walkthrough" }),
  ).toBeVisible();
  await page.getByLabel("Full name").fill("Kalina Ruseva");
  await page.getByLabel("Phone").fill("+359 88 412 903");
  await page.getByRole("link", { name: "Cancel" }).click();
});

test("workspace link from the landing reaches sign-in", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Open the workspace" }).first().click();
  await expect(page).toHaveURL(/\/app/);
  await expect(
    page.getByRole("heading", {
      name: /Welcome back\.|Waking the hospital workspace/,
    }),
  ).toBeVisible();
});
