import { expect, test } from "@playwright/test";

const clipPath = (el: Element) => getComputedStyle(el).clipPath;

test("the hero opens from a letterboxed aperture and settles unclipped", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "no-preference" });
  // Record whether the frame was ever clipped; the opening lasts ~1.6s.
  await page.addInitScript(() => {
    const w = window as unknown as { sawAperture?: boolean };
    const watch = () => {
      const frame = document.querySelector(".mc-hero-frame");
      if (frame && getComputedStyle(frame).clipPath.startsWith("inset"))
        w.sawAperture = true;
      if (!w.sawAperture) requestAnimationFrame(watch);
    };
    requestAnimationFrame(watch);
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /More presence.*Less process/ }),
  ).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(
        () => (window as unknown as { sawAperture?: boolean }).sawAperture,
      ),
    )
    .toBe(true);
  const frame = page.locator(".mc-hero-frame");
  await expect.poll(() => frame.evaluate(clipPath)).toBe("none");
  await expect
    .poll(() =>
      page
        .locator(".mc-hero h1 .mc-line > span")
        .first()
        .evaluate((el) => getComputedStyle(el).transform),
    )
    .toMatch(/^(none|matrix\(1, 0, 0, 1, 0, 0\))$/);
});

test("reduced motion shows the complete, still page", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(page.locator(".mc")).toHaveAttribute("data-ready", "true");
  expect(await page.locator(".mc-hero-frame").evaluate(clipPath)).toBe("none");
  const words = page.locator(".mc-intro .mc-word");
  await expect(words.first()).toHaveCSS("opacity", "1");
  await expect(words.last()).toHaveCSS("opacity", "1");
  expect(
    await page
      .locator(".mc")
      .evaluate((el) => getComputedStyle(el, "::after").animationName),
  ).toBe("none");
  await expect(page.locator(".mc-trail canvas")).toHaveCount(0);
  await expect(page.locator(".mc-hero-frame .mc-ambient canvas")).toHaveCount(
    0,
  );
});

test("the intro sentence assembles as it scrolls into view", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/");
  await expect(page.locator(".mc")).toHaveAttribute("data-motion", "full");
  const lastWord = page.locator(".mc-intro .mc-word").last();
  await expect
    .poll(() => lastWord.evaluate((el) => Number(getComputedStyle(el).opacity)))
    .toBeLessThan(0.5);
  await expect(page.locator("#intro-title")).toHaveText(
    /A hospital is a thousand moving parts\.\s*Bring them together\./,
  );
  await page.locator(".mc-capabilities").scrollIntoViewIfNeeded();
  await expect
    .poll(() => lastWord.evaluate((el) => Number(getComputedStyle(el).opacity)))
    .toBeGreaterThan(0.95);
});

test("product tabs wipe to the next view and leave a single shot", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/");
  await expect(page.locator(".mc")).toHaveAttribute("data-motion", "full");
  await page.locator(".mc-platform-panel").scrollIntoViewIfNeeded();
  await page.getByRole("tab", { name: "Your care team" }).click();
  const shots = page.locator(".mc-product-shot");
  await expect(shots).toHaveCount(2);
  await expect(shots).toHaveCount(1);
  await expect(shots.getByRole("img")).toHaveAttribute("alt", /your care team/);
  await expect
    .poll(() => shots.evaluate((el) => getComputedStyle(el).clipPath))
    .toMatch(/^(none|inset\(0(px|%)\))$/);
});

test("primary actions follow a mouse pointer and settle back", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/");
  await expect(page.locator(".mc")).toHaveAttribute("data-motion", "full");
  const magnet = page.locator(".mc-hero .mc-magnetic");
  await page.waitForTimeout(2200);
  const box = (await magnet.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.move(box.x + box.width - 3, box.y + 3, { steps: 6 });
  await expect
    .poll(() =>
      magnet.evaluate(
        (el) => new DOMMatrix(getComputedStyle(el).transform).m41,
      ),
    )
    .toBeGreaterThan(4);
  await page.mouse.move(10, 880, { steps: 3 });
  await expect
    .poll(() => magnet.evaluate((el) => getComputedStyle(el).transform))
    .toBe("none");
  await magnet.getByRole("link", { name: "Open the workspace" }).click();
  await expect(page).toHaveURL(/\/app/);
});

test("Canvas UI layers mount on desktop only", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/");
  await expect(
    page.locator(".mc-hero-frame .mc-ambient canvas").first(),
  ).toBeAttached();
  await page.locator(".mc-close").scrollIntoViewIfNeeded();
  await expect(page.locator(".mc-trail canvas").first()).toBeAttached();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".mc-trail canvas")).toHaveCount(0);
  await expect(page.locator(".mc-hero-frame .mc-ambient canvas")).toHaveCount(
    0,
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    ),
  ).toBeLessThanOrEqual(0);
});
