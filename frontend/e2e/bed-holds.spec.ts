import { expect, test, type Page } from "@playwright/test";

async function mockRooms(page: Page, rejectHold = false) {
  let holds: {
    id: number;
    bedCount: number;
    reason: string;
    startsAt: string;
    endsAt: string;
  }[] = [];
  let nextId = 1;
  let writes = 0;
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace("/api/v1", "");
    if (path === "/health") return route.fulfill({ json: { status: "UP" } });
    if (path === "/auth/me")
      return route.fulfill({
        json: { id: 1, username: "admin", role: "ADMIN" },
      });
    if (path === "/auth/csrf")
      return route.fulfill({
        json: { headerName: "X-CSRF-TOKEN", token: "test" },
      });
    if (path === "/demo/status")
      return route.fulfill({ json: { enabled: false } });
    if (path === "/workspaces")
      return route.fulfill({
        json: {
          activeDepartmentId: 1,
          hospitals: [
            {
              id: 1,
              name: "Test Hospital",
              owner: true,
              departments: [{ id: 1, name: "Test Ward", role: "ADMIN" }],
            },
          ],
        },
      });
    if (path === "/operations/stream") return route.abort(); // No SSE to hide a broken invalidation.
    if (path === "/rooms/303/holds" && request.method() === "POST") {
      writes++;
      if (rejectHold || holds.length === 2)
        return route.fulfill({
          status: 409,
          json: {
            code: "ROOM_CAPACITY_EXCEEDED",
            message:
              "The room does not have enough unoccupied capacity for this hold window.",
          },
        });
      const hold = { ...request.postDataJSON(), id: nextId++ };
      holds.push(hold);
      return route.fulfill({ status: 201, json: hold });
    }
    if (path.startsWith("/rooms/303/holds/") && request.method() === "DELETE") {
      holds = holds.filter(
        (hold) => hold.id !== Number(path.split("/").at(-1)),
      );
      return route.fulfill({ status: 204 });
    }
    if (path === "/rooms") {
      const room = {
        id: 303,
        roomNumber: "303",
        bedCount: 4,
        active: true,
        occupiedBeds: 2,
        heldBeds: holds.length,
        availableBeds: 2 - holds.length,
        activeHeldBeds: holds.length,
        capabilities: [],
        holds,
      };
      const items =
        room.availableBeds >= Number(url.searchParams.get("minFree") || 0)
          ? [room]
          : [];
      return route.fulfill({
        json: {
          items,
          page: 0,
          size: 20,
          totalElements: items.length,
          totalPages: items.length,
          hasNext: false,
          nextPage: null,
        },
      });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto("/app/rooms?q=303");
  await expect(page.locator(".room-card")).toContainText(
    "2 occupied · 0 held / 4",
  );
  return { writes: () => writes };
}

async function saveHold(page: Page, reason: string) {
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Reason", { exact: true }).fill(reason);
  await dialog.getByRole("button", { name: "Save hold", exact: true }).click();
}

test("holds confirm success, update the card immediately, reject a third, and release to zero", async ({
  page,
}) => {
  const state = await mockRooms(page);
  const card = page.locator(".room-card");
  const dialog = page.getByRole("dialog");
  await card.getByRole("button", { name: "Manage holds" }).click();
  for (const [index, reason] of [
    "First inspection",
    "Second inspection",
  ].entries()) {
    await saveHold(page, reason);
    await expect(dialog.getByRole("status")).toHaveText(
      "Hold saved: 1 bed reserved in room 303.",
    );
    await expect(dialog.locator(".room-hold-list article")).toHaveCount(
      index + 1,
    );
    await expect(card).toContainText(`2 occupied · ${index + 1} held / 4`);
    await expect(card.locator(".beds .held")).toHaveCount(index + 1);
  }
  expect(state.writes()).toBe(2);
  await saveHold(page, "No capacity remaining");
  await expect(dialog.getByRole("alert")).toContainText(
    "does not have enough unoccupied capacity",
  );
  await expect(dialog.getByRole("status")).toBeEmpty();
  await expect(dialog.locator(".room-hold-list article")).toHaveCount(2);
  await dialog.getByRole("button", { name: "Close dialog" }).click();
  await expect(card).toContainText("No placement capacity");
  await card.getByRole("button", { name: "Manage holds" }).click();
  await expect(dialog.locator(".room-hold-list article")).toHaveCount(2);
  for (const remaining of [1, 0]) {
    await dialog
      .getByRole("button", { name: "Release", exact: true })
      .first()
      .click();
    await expect(dialog.getByRole("status")).toHaveText(
      "Hold released in room 303.",
    );
    await expect(dialog.locator(".room-hold-list article")).toHaveCount(
      remaining,
    );
    await expect(card).toContainText(`2 occupied · ${remaining} held / 4`);
  }
  await dialog.getByRole("button", { name: "Close dialog" }).click();
  await expect(card).toContainText("2 available");
  await page.reload();
  await expect(card).toContainText("2 occupied · 0 held / 4");
});

test("a fully held room leaves Available only while its dialog stays current and can release holds", async ({
  page,
}) => {
  await mockRooms(page);
  await page.getByLabel("Available only").check();
  await page.getByRole("button", { name: "Manage holds" }).click();
  for (const reason of ["First", "Second"]) {
    await saveHold(page, reason);
    await expect(page.getByRole("dialog").getByRole("status")).toContainText(
      "Hold saved",
    );
  }
  const dialog = page.getByRole("dialog");
  await expect(page.locator(".room-card")).toHaveCount(0);
  await expect(dialog.locator(".room-hold-list article")).toHaveCount(2);
  await expect(dialog).toContainText("2 occupied · 2 held / 4 · 0 available");
  await dialog
    .getByRole("button", { name: "Release", exact: true })
    .first()
    .click();
  await expect(dialog.locator(".room-hold-list article")).toHaveCount(1);
  await expect(page.locator(".room-card")).toContainText(
    "2 occupied · 1 held / 4",
  );
});

test("failed holds show an error without a success confirmation or changed capacity", async ({
  page,
}) => {
  await mockRooms(page, true);
  await page.getByRole("button", { name: "Manage holds" }).click();
  await saveHold(page, "Rejected inspection");
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("alert")).toContainText(
    "does not have enough unoccupied capacity",
  );
  await expect(dialog.getByRole("status")).toBeEmpty();
  await expect(dialog.getByLabel("Reason", { exact: true })).toHaveValue(
    "Rejected inspection",
  );
  await expect(page.locator(".room-card")).toContainText(
    "2 occupied · 0 held / 4",
  );
});
