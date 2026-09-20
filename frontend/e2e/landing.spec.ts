import { expect, test } from "@playwright/test";

test("cinematic landing opens on the public home and can book", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Advanced care. Human at heart." }),
  ).toBeVisible();
  await page.locator("[data-film-book]").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Book an Appointment" }),
  ).toBeVisible();
  await page.getByLabel("Full name").fill("Kalina Ruseva");
  await page.getByLabel("Phone").fill("+359 88 412 903");
  await page.getByRole("link", { name: "Cancel" }).click();
  await page.getByRole("link", { name: "Find a Specialist" }).click();
  await expect(
    page.getByRole("heading", { name: "Meet your care team" }),
  ).toBeVisible();
});

test("staff sign-in from the landing reaches the workspace login", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Menu" }).click();
  await page.getByRole("link", { name: "Staff sign-in" }).click();
  await expect(page).toHaveURL(/\/app/);
  await expect(
    page.getByRole("heading", {
      name: /Welcome back\.|Waking the hospital workspace/,
    }),
  ).toBeVisible();
});
