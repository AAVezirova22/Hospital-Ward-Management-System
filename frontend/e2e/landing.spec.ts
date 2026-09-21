import { expect, test } from "@playwright/test";

test("product landing opens the assistant and guided walkthrough", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /More presence.*Less process/ }),
  ).toBeVisible();
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("link", { name: "Assistant", exact: true })
    .click();
  await expect(
    page.getByRole("heading", {
      name: /Files in.*A plan out/,
    }),
  ).toBeVisible();
  await page.locator("[data-film-book]").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Your ward, in one view." }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "From files to a plan." }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Stay connected with iMessage." }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
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
