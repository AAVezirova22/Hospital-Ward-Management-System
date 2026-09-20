import { expect, test } from "@playwright/test";

test("Canvas UI renders, pauses, survives context loss and respects saved preferences", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.emulateMedia({
    colorScheme: "dark",
    reducedMotion: "no-preference",
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Welcome back." }),
  ).toBeVisible();
  const output = page.locator('.cinema-clouds > canvas[aria-hidden="true"]');
  await expect(output).toBeVisible();
  await expect
    .poll(() =>
      output.evaluate((canvas: HTMLCanvasElement) => ({
        width: canvas.width,
        gpu: Boolean(canvas.getContext("webgl2")),
      })),
    )
    .toMatchObject({ gpu: true });
  await page.mouse.move(530, 300);
  await expect(page.locator("html")).toHaveAttribute("data-motion", "on");
  await page.screenshot({
    path: "test-results/cinematic-sign-in.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Pause cinematic motion" }).click();
  await expect(output).toHaveCount(0);
  await expect(page.locator("html")).toHaveAttribute("data-motion", "off");
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Play cinematic motion" }),
  ).toBeVisible();
  await expect(output).toHaveCount(0);
  await page.getByRole("button", { name: "Play cinematic motion" }).click();
  await expect(output).toBeVisible();
  await output.evaluate((canvas: HTMLCanvasElement) => {
    canvas
      .getContext("webgl2")
      ?.getExtension("WEBGL_lose_context")
      ?.loseContext();
  });
  await expect(output).toHaveCSS("visibility", "hidden");
  await expect(page.getByLabel("Username", { exact: true })).toBeEditable();
  await expect(page.locator(".atrium-image")).toBeVisible();
  expect(errors).toEqual([]);
});

test("reduced motion and small screens keep static artwork without a GPU layer", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Welcome back." }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Motion reduced by system preference" }),
  ).toBeDisabled();
  await expect(page.locator(".cinema-clouds")).toHaveCount(0);
  await expect(page.locator(".atrium-image")).toHaveCSS(
    "animation-name",
    "none",
  );
  await expect(page.locator(".title-line").first()).toHaveCSS(
    "transform",
    "none",
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await expect(
    page.getByRole("button", { name: "Pause cinematic motion" }),
  ).toBeVisible();
  await expect(page.locator(".cinema-clouds")).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: "test-results/cinematic-mobile.png",
    fullPage: true,
  });
});
