import { expect, test } from "@playwright/test";

// Opt in against a seeded local instance. Only this test's holds are released;
// existing holds and patient placements remain untouched.
test("real holds persist, update capacity, reject overflow, and release", async ({
  page,
}) => {
  test.skip(
    !process.env.E2E_PASSWORD,
    "Set E2E_PASSWORD for the local seeded instance.",
  );
  await page.goto("/app/rooms");
  const login = page.locator("form").filter({
    has: page.getByRole("button", { name: "Sign in", exact: true }),
  });
  await login
    .getByPlaceholder("Enter your username", { exact: true })
    .fill("admin");
  await login
    .getByPlaceholder("Enter your password", { exact: true })
    .fill(process.env.E2E_PASSWORD!);
  await login.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("link", { name: "Room capacity", exact: true }).click();
  await expect(page.locator(".room-card").first()).toBeVisible();
  const department = await page.evaluate(() => {
    const key = Object.keys(localStorage).find((key) =>
      key.startsWith("medcore-department:"),
    );
    return key ? localStorage.getItem(key) : null;
  });
  const headers: Record<string, string> = department
    ? { "X-Department-Id": department }
    : {};
  const response = await page.request.get("/api/v1/rooms?minFree=2&size=100", {
    headers,
  });
  expect(response.ok()).toBeTruthy();
  const rooms = (await response.json()).items;
  const room =
    rooms.find((r: { roomNumber: string }) => r.roomNumber === "303") ??
    rooms[0];
  expect(
    room,
    "A seeded room with at least two available beds is required",
  ).toBeTruthy();
  const baseline = room.heldBeds;
  const created: number[] = [];
  const prefix = `Hold regression ${Date.now()}`;
  try {
    await page.goto(`/app/rooms?room=${room.id}`);
    const card = page.locator(".room-card");
    const dialog = page.getByRole("dialog");
    await card.getByRole("button", { name: "Manage holds" }).click();
    let added = 0;
    for (const [index, count] of [1, room.availableBeds - 1].entries()) {
      const reason = `${prefix} ${index}`;
      await dialog.getByLabel("Beds to hold").fill(String(count));
      await dialog.getByLabel("Reason", { exact: true }).fill(reason);
      const saved = page.waitForResponse(
        (r) =>
          r.url().endsWith(`/rooms/${room.id}/holds`) &&
          r.request().method() === "POST",
      );
      await dialog
        .getByRole("button", { name: "Save hold", exact: true })
        .click();
      const result = await saved;
      expect(result.status()).toBe(201);
      created.push((await result.json()).id);
      added += count;
      await expect(dialog.getByRole("status")).toHaveText(
        `Hold saved: ${count} bed${count === 1 ? "" : "s"} reserved in room ${room.roomNumber}.`,
      );
      await expect(
        dialog.locator(".room-hold-list article").filter({ hasText: reason }),
      ).toBeVisible();
      await expect(card).toContainText(
        `${room.occupiedBeds} occupied · ${baseline + added} held / ${room.bedCount}`,
      );
      await expect(card.locator(".beds .held")).toHaveCount(
        Math.min(baseline + added, 12 - room.occupiedBeds),
      );
    }
    await page.screenshot({
      path: "test-results/bed-holds-success.png",
      fullPage: true,
    });
    await dialog.getByRole("button", { name: "Close dialog" }).click();
    await expect(card).toContainText("No placement capacity");
    await page.reload();
    await expect(card).toContainText(
      `${baseline + room.availableBeds} held / ${room.bedCount}`,
    );
    await card.getByRole("button", { name: "Manage holds" }).click();
    await dialog.getByLabel("Beds to hold").fill("1");
    await dialog
      .getByLabel("Reason", { exact: true })
      .fill(`${prefix} overflow`);
    await dialog
      .getByRole("button", { name: "Save hold", exact: true })
      .click();
    await expect(dialog.getByRole("alert")).toContainText(
      "does not have enough unoccupied capacity",
    );
    await expect(dialog.getByRole("status")).toBeEmpty();
    for (const index of [0, 1]) {
      await dialog
        .locator(".room-hold-list article")
        .filter({ hasText: `${prefix} ${index}` })
        .getByRole("button", { name: "Release", exact: true })
        .click();
      await expect(dialog.getByRole("status")).toHaveText(
        `Hold released in room ${room.roomNumber}.`,
      );
      await expect(
        dialog
          .locator(".room-hold-list article")
          .filter({ hasText: `${prefix} ${index}` }),
      ).toHaveCount(0);
    }
    await dialog.getByRole("button", { name: "Close dialog" }).click();
    await expect(card).toContainText(
      `${room.occupiedBeds} occupied · ${baseline} held / ${room.bedCount}`,
    );
    await expect(card).toContainText(`${room.availableBeds} available`);
    await page.reload();
    await expect(card).toContainText(`${baseline} held / ${room.bedCount}`);
    await page.screenshot({
      path: "test-results/bed-holds-restored.png",
      fullPage: true,
    });
  } finally {
    const csrfResponse = await page.request.get("/api/v1/auth/csrf");
    const csrf = await csrfResponse.json();
    for (const id of created) {
      const response = await page.request.delete(
        `/api/v1/rooms/${room.id}/holds/${id}`,
        {
          headers: { ...headers, [csrf.headerName]: csrf.token },
        },
      );
      expect(response.status(), `Cleanup of test hold ${id}`).toBe(204);
    }
  }
});
