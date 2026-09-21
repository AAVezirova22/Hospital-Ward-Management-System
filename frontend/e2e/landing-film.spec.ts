import { expect, test } from "@playwright/test";

for (const width of [390, 768, 1440]) {
  test(`conversation plays without scrolling and holds its ending at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({
      reducedMotion: "no-preference",
      colorScheme: "light",
    });
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto("/");
    await expect(page.locator(".mc")).toHaveAttribute("data-ready", "true");
    const film = page.locator(".mc-phone-film");
    await film.scrollIntoViewIfNeeded();
    await expect(film).toHaveAttribute("data-playing", "true");
    const scroll = await page.evaluate(() => scrollY);
    await page.screenshot({ path: `../phone-start-${width}.png` });
    await expect(film).toHaveAttribute("data-complete", "true", {
      timeout: 22000,
    });
    await expect(page.locator(".mc-phone-handoff")).toHaveCSS("opacity", "1");
    await expect(page.locator(".mc-message-thread .is-received")).toHaveCSS(
      "opacity",
      "1",
    );
    expect(
      Math.abs((await page.evaluate(() => scrollY)) - scroll),
    ).toBeLessThan(3);
    const phone = await page.getByTestId("cinematic-phone").boundingBox();
    const workspace = await page.locator(".mc-phone-workspace").boundingBox();
    const copy = await page.locator(".mc-messages-copy").boundingBox();
    expect(phone!.x).toBeGreaterThanOrEqual(0);
    expect(workspace!.x + workspace!.width).toBeLessThanOrEqual(width);
    if (width > 900)
      expect(workspace!.x + workspace!.width).toBeLessThan(copy!.x);
    await page.screenshot({ path: `../phone-end-${width}.png` });
    const ending = await page
      .getByTestId("cinematic-phone")
      .getAttribute("style");
    await page.waitForTimeout(1500);
    expect(
      await page.getByTestId("cinematic-phone").getAttribute("style"),
    ).toBe(ending);
    await page.getByRole("button", { name: "Replay conversation" }).click();
    await expect(film).toHaveAttribute("data-complete", "false");
    await expect(film).toHaveAttribute("data-playing", "true");
    expect(errors).toEqual([]);
  });
}

test("local pause and offscreen suspension preserve playback", async ({
  page,
}) => {
  await page.goto("/");
  const film = page.locator(".mc-phone-film");
  await film.scrollIntoViewIfNeeded();
  await expect(film).toHaveAttribute("data-playing", "true");
  await page.waitForTimeout(3500);
  await page.getByRole("button", { name: "Pause conversation" }).click();
  const phone = page.getByTestId("cinematic-phone");
  const paused = await phone.getAttribute("style");
  await page.waitForTimeout(1200);
  expect(await phone.getAttribute("style")).toBe(paused);
  await page
    .getByRole("button", { name: "Play conversation", exact: true })
    .click();
  await expect(film).toHaveAttribute("data-playing", "true");
  await page.locator("#product").scrollIntoViewIfNeeded();
  await expect(film).toHaveAttribute("data-playing", "false");
  const away = await phone.getAttribute("style");
  await page.waitForTimeout(1000);
  expect(await phone.getAttribute("style")).toBe(away);
  await film.scrollIntoViewIfNeeded();
  await expect(film).toHaveAttribute("data-playing", "true");
});

test("reduced motion and global pause leave a readable, stable chapter", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  const film = page.locator(".mc-phone-film");
  await film.scrollIntoViewIfNeeded();
  await expect(page.locator(".mc canvas")).toHaveCount(0);
  await expect(page.locator(".mc-message-thread .is-received")).toHaveCSS(
    "opacity",
    "1",
  );
  await page
    .getByRole("button", { name: "Start a workflow", exact: true })
    .click();
  await expect(page.locator(".mc-message-file")).toBeVisible();
  const before = await page.locator("#workflow").boundingBox();
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.getByRole("button", { name: "Pause animations" }).click();
  const after = await page.locator("#workflow").boundingBox();
  expect(Math.abs(before!.height - after!.height)).toBeLessThan(2);
  await expect(page.locator(".mc-message-file")).toHaveCSS("opacity", "1");
});

test("hero has accurate sizes and Canvas UI degrades on context loss", async ({
  page,
}) => {
  const warnings: string[] = [];
  page.on("console", (message) => {
    if (/sizes.*100vw/.test(message.text())) warnings.push(message.text());
  });
  await page.goto("/");
  await expect(page.locator(".mc-hero-image img")).toHaveAttribute(
    "sizes",
    /calc\(100vw - 44px\)/,
  );
  const canvas = page.locator(".mc-hero-frame .clouds-output");
  await expect(canvas).toBeAttached();
  await canvas.dispatchEvent("webglcontextlost");
  await expect(canvas).toHaveClass(/clouds-output-failed/);
  await expect(
    page.getByRole("heading", { name: /More presence/ }),
  ).toBeVisible();
  await expect(page.locator(".mc-hero .mc-actions")).toHaveCSS("opacity", "1");
  await page.screenshot({ path: "../hero.png" });
  expect(warnings).toEqual([]);
});

test("scroll camera changes the product transform and restores it on pause", async ({
  page,
}) => {
  const violations: string[] = [];
  page.on("console", (message) => {
    if (
      message.text().includes("violates the following Content Security Policy")
    )
      violations.push(message.text());
  });
  await page.goto("/");
  await expect(page.locator(".mc")).toHaveAttribute("data-ready", "true");
  await page.evaluate(() => {
    document.documentElement.style.scrollBehavior = "auto";
  });
  const product = page.locator(".mc-product-bezel");
  const chapterY = await page
    .locator(".mc-platform-panel")
    .evaluate((element) => element.getBoundingClientRect().top + scrollY);
  await page.evaluate((y) => scrollTo(0, y - innerHeight * 0.9), chapterY);
  await page.waitForTimeout(1300);
  const entrance = await product.evaluate(
    (element) => getComputedStyle(element).transform,
  );
  await page.evaluate((y) => scrollTo(0, y - innerHeight * 0.25), chapterY);
  await page.waitForTimeout(1300);
  const settled = await product.evaluate(
    (element) => getComputedStyle(element).transform,
  );
  expect(entrance).not.toBe(settled);
  await page.screenshot({ path: "../platform.png" });
  await page.getByRole("button", { name: "Pause animations" }).click();
  await expect(product).toHaveCSS("transform", "none");
  expect(violations).toEqual([]);
});

test("the page remains readable without JavaScript", async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /More presence/ }),
  ).toBeVisible();
  await page.locator("#workflow").scrollIntoViewIfNeeded();
  await expect(page.locator(".mc-message-thread .is-received")).toHaveCSS(
    "opacity",
    "1",
  );
  await expect(page.locator(".mc-messages-copy")).toHaveCSS("opacity", "1");
  await context.close();
});
