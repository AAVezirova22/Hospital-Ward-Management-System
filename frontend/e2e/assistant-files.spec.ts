import { test, expect, type Page } from "@playwright/test";

async function openAssistant(page: Page) {
  await page.goto("/app");
  await page.getByLabel("Username", { exact: true }).fill("admin");
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.E2E_PASSWORD || "IntegrationPassword123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Your ward. In the moment." }),
  ).toBeVisible();
  await page.locator(".assistant-launch").click();
  await expect(
    page.getByRole("button", { name: "Upload files", exact: true }),
  ).toBeVisible();
}

test("uploads and removes a real attachment and explains local mode", async ({
  page,
}) => {
  await openAssistant(page);
  await page.getByLabel("Upload assistant files").setInputFiles({
    name: "schedule.csv",
    mimeType: "text/csv",
    buffer: Buffer.from("room,beds\n301,2"),
  });
  await expect(
    page.getByRole("button", { name: "Remove schedule.csv" }),
  ).toBeVisible();
  await page
    .getByLabel("Assistant message", { exact: true })
    .fill("Build my workflow from this file");
  await page.getByRole("button", { name: "Send message" }).click();
  await expect(
    page.getByText(
      /File-based workflow planning requires a configured external AI model/,
    ),
  ).toBeVisible();
  await page.getByRole("button", { name: "Remove schedule.csv" }).click();
  await expect(
    page.getByRole("button", { name: "Remove schedule.csv" }),
  ).toHaveCount(0);
});

test("reads a connected file only on agent request and reviews before confirming", async ({
  page,
}) => {
  await page.addInitScript(() => {
    let reads = 0;
    Object.defineProperty(window, "__fileReads", { get: () => reads });
    Object.defineProperty(window, "showDirectoryPicker", {
      value: async () => ({
        kind: "directory",
        name: "Documents",
        async *values() {
          yield {
            kind: "file",
            name: "workflow.csv",
            getFile: async () => {
              reads++;
              return new File(["room,beds\n301,2"], "workflow.csv", {
                type: "text/csv",
              });
            },
          };
        },
      }),
    });
  });
  let turns = 0,
    confirms = 0;
  await page.route("**/api/v1/assistant/messages", async (route) => {
    const input = route.request().postDataJSON();
    turns++;
    if (turns === 1) {
      expect(input.connectedFiles).toHaveLength(1);
      expect(input.sourceIds).toHaveLength(0);
      await route.fulfill({
        json: {
          responseType: "FILE_REQUEST",
          message: "Read relevant data",
          sessionId: null,
          model: "browser-fixture",
          data: { ids: [input.connectedFiles[0].id] },
        },
      });
    } else {
      expect(input.sourceIds).toHaveLength(1);
      expect(input.connectedFiles).toHaveLength(0);
      await route.fulfill({
        json: {
          responseType: "WORKFLOW_PROPOSAL",
          message: "Review the room setup",
          sessionId: null,
          model: "browser-fixture",
          data: {
            action: {
              id: 99999999,
              actionType: "WORKFLOW",
              status: "PENDING",
              expiresAt: new Date(Date.now() + 300000).toISOString(),
            },
            workflow: {
              title: "Prepare room 301",
              steps: [
                {
                  key: "r1",
                  operation: "createRoom",
                  source: "workflow.csv",
                  fields: { roomNumber: "301", bedCount: 2, active: true },
                },
              ],
            },
          },
        },
      });
    }
  });
  await page.route("**/api/v1/ai-actions/99999999/confirm", async (route) => {
    confirms++;
    await route.fulfill({ json: { created: { r1: 123 } } });
  });
  await openAssistant(page);
  await page
    .getByRole("button", { name: "Connect folder", exact: true })
    .click();
  await expect(
    page.getByText("1 readable file names connected."),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as unknown as { __fileReads: number }).__fileReads,
    ),
  ).toBe(0);
  await page
    .getByLabel("Assistant message", { exact: true })
    .fill("Set up the rooms from my files");
  await page.getByRole("button", { name: "Send message" }).click();
  await expect(
    page.getByRole("heading", { name: "Prepare room 301" }),
  ).toBeVisible();
  expect(turns).toBe(2);
  expect(confirms).toBe(0);
  expect(
    await page.evaluate(
      () => (window as unknown as { __fileReads: number }).__fileReads,
    ),
  ).toBe(1);
  await expect(page.getByText("Source: workflow.csv")).toBeVisible();
  await page.screenshot({
    path: "test-results/assistant-workflow-desktop.png",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({ path: "test-results/assistant-workflow-mobile.png" });
  await page
    .getByRole("button", { name: "Confirm workflow", exact: true })
    .click();
  await expect(
    page.locator(".workflow-proposal").getByText("EXECUTED", { exact: true }),
  ).toBeVisible();
  expect(confirms).toBe(1);
  await page.getByRole("button", { name: "Disconnect folder" }).click();
  await expect(
    page.getByRole("button", { name: "Remove workflow.csv" }),
  ).toHaveCount(0);
});
