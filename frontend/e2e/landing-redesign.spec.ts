import { expect, test } from "@playwright/test";

test("social preview marks sample ward metrics as illustrative", async ({
  page,
}) => {
  await page.goto("/");

  const openGraphImage = page.locator('meta[property="og:image"]');
  const imageAlt = page.locator('meta[property="og:image:alt"]');
  const twitterImage = page.locator('meta[name="twitter:image"]');
  const twitterImageAlt = page.locator('meta[name="twitter:image:alt"]');

  await expect(openGraphImage).toHaveAttribute(
    "content",
    /\/opengraph-image(?:\?.*)?$/,
  );
  await expect(imageAlt).toHaveAttribute("content", /illustrative.*sample.*not live/i);
  await expect(twitterImage).toHaveAttribute(
    "content",
    /\/opengraph-image(?:\?.*)?$/,
  );
  await expect(twitterImageAlt).toHaveAttribute(
    "content",
    /illustrative.*sample.*not live/i,
  );

  const response = await page.request.get("/opengraph-image");
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("image/png");
  const image = await response.body();
  expect(image.subarray(0, 8)).toEqual(
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  );
  expect(image.readUInt32BE(16)).toBe(1200);
  expect(image.readUInt32BE(20)).toBe(630);
});

test("sample workflow reviews, cancels, and confirms without making API writes", async ({
  page,
}) => {
  const writes: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/") && request.method() !== "GET")
      writes.push(request.url());
  });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page
    .getByRole("button", { name: "Use sample files", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Prepare an admission" }),
  ).toBeVisible();
  await expect(
    page.getByText("Source: sample-admissions.csv").first(),
  ).toBeVisible();
  await page.getByRole("button", { name: "Cancel proposal" }).click();
  await expect(page.getByRole("status")).toContainText("Nothing has changed");
  await page.getByRole("button", { name: "Start again", exact: true }).click();
  await page
    .getByRole("button", { name: "Use sample files", exact: true })
    .click();
  await page.getByRole("button", { name: "Confirm workflow" }).click();
  await expect(page.getByRole("status")).toContainText(
    "No hospital records were created",
  );
  expect(writes).toEqual([]);
});

test("sample drag and drop prepares a review while personal files stay local", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  const personal = await page.evaluateHandle(() => {
    const dt = new DataTransfer();
    dt.items.add(
      new File(["Private contents"], "private.csv", { type: "text/csv" }),
    );
    return dt;
  });
  await page
    .locator(".mc-dropzone")
    .dispatchEvent("drop", { dataTransfer: personal });
  await expect(page.getByRole("status")).toContainText(
    "open the secure workspace",
  );
  await expect(
    page.getByRole("button", { name: "Confirm workflow" }),
  ).toHaveCount(0);
  const sample = await page.evaluateHandle(() => {
    const dt = new DataTransfer();
    dt.setData("application/medcore-sample", "sample-admissions");
    return dt;
  });
  await page
    .locator(".mc-dropzone")
    .dispatchEvent("drop", { dataTransfer: sample });
  await expect(
    page.getByRole("button", { name: "Confirm workflow" }),
  ).toBeVisible();
});

test("platform tabs work with keyboard and expose their corresponding routes", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page.getByRole("tab", { name: "Ward overview" }).focus();
  await page.keyboard.press("ArrowRight");
  await expect(
    page.getByRole("tab", { name: "Patients & beds" }),
  ).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("tabpanel")).toContainText(
    "A place for every patient.",
  );
  await expect(page.getByRole("tabpanel").getByRole("link")).toHaveAttribute(
    "href",
    "/app/planner",
  );
  await page.keyboard.press("End");
  await expect(page.getByRole("tabpanel")).toContainText(
    "Know what the day adds up to.",
  );
  await expect(page.getByRole("tabpanel").getByRole("link")).toHaveAttribute(
    "href",
    "/app/reports",
  );
});

test("assistant examples respond to selection", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await page
    .getByRole("button", { name: "Start a workflow", exact: true })
    .click();
  await expect(page.locator(".mc-conversation")).toContainText(
    "Prepare the admissions from this file",
  );
  await page
    .getByRole("button", { name: "Check the ward", exact: true })
    .click();
  await expect(page.locator(".mc-conversation")).toContainText(
    "14 active admissions",
  );
  await expect(
    page.getByRole("button", { name: "Check the ward", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
});

test("landing copy does not advertise an unimplemented messaging integration", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await expect(page.locator("body")).not.toContainText(/iMessage/i);

  const descriptions = await page
    .locator(
      'meta[name="description"], meta[property="og:description"], meta[name="twitter:description"]',
    )
    .evaluateAll((elements) =>
      elements.map((element) => element.getAttribute("content") ?? ""),
    );
  expect(descriptions).not.toHaveLength(0);
  expect(descriptions.join(" ")).not.toMatch(/iMessage|\bMessages\b/i);
  await expect(page.locator(".mc-message-note")).toContainText(
    "illustrative assistant preview",
  );
  await expect(page.locator(".mc-message-note")).toContainText(
    "Medcore workspace",
  );
});

test("mobile menu traps focus, follows anchors, and restores focus on Escape", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page.getByRole("button", { name: "Open menu" }).click();
  await expect(page.getByRole("dialog", { name: "Site menu" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("button", { name: "Open menu" })).toBeFocused();
  await page.getByRole("button", { name: "Open menu" }).click();
  await page
    .getByRole("navigation", { name: "Mobile navigation" })
    .getByRole("link", { name: "Assistant", exact: true })
    .click();
  await expect(page).toHaveURL(/#assistant$/);
  await expect(
    page.getByRole("dialog", { name: "Site menu" }),
  ).not.toBeVisible();
  await expect(
    page.getByRole("heading", { name: /Files in.*A plan out/ }),
  ).toBeInViewport();
});

test("reduced motion suppresses WebGL and pinning; theme choice persists", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce", colorScheme: "light" });
  await page.goto("/");
  await expect(page.locator(".mc")).toHaveAttribute("data-motion", "reduced");
  await expect(page.locator(".mc canvas")).toHaveCount(0);
  await expect(page.locator(".pin-spacer")).toHaveCount(0);
  await page.getByRole("button", { name: "Use dark theme" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-site-theme", "dark");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-site-theme", "dark");
  await expect(
    page.getByRole("heading", { name: /More presence/ }),
  ).toBeVisible();
});

test("live motion changes unmount canvas and clean up pinned choreography", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/");
  await expect(
    page.locator(".mc-hero-frame .mc-ambient canvas").last(),
  ).toBeAttached();
  await expect(page.locator(".pin-spacer")).toHaveCount(1);
  await page.getByRole("button", { name: "Pause animations" }).click();
  await expect(page.locator(".mc canvas")).toHaveCount(0);
  await expect(page.locator(".pin-spacer")).toHaveCount(0);
  await page.reload();
  await expect(page.locator(".mc")).toHaveAttribute("data-motion", "reduced");
  await page.getByRole("button", { name: "Enable animations" }).click();
  await expect(page.locator(".pin-spacer")).toHaveCount(1);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(page.locator(".pin-spacer")).toHaveCount(0);
  expect(errors).toEqual([]);
});

for (const width of [360, 768, 1440]) {
  test(`complete landing has working images and no overflow at ${width}px`, async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/");
    for (const id of [
      "product",
      "platform",
      "specialists",
      "assistant",
      "workflow",
      "technology",
      "start",
    ]) {
      await page.locator(`#${id}`).scrollIntoViewIfNeeded();
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true);
    }
    await expect
      .poll(() =>
        page
          .locator("main img")
          .evaluateAll((images) =>
            images.every(
              (image) =>
                (image as HTMLImageElement).complete &&
                (image as HTMLImageElement).naturalWidth > 0,
            ),
          ),
      )
      .toBe(true);
    expect(errors).toEqual([]);
  });
}
