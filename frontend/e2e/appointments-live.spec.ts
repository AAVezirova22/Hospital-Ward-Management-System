import { expect, test } from "@playwright/test";
test.use({ timezoneId: "America/New_York" });

// Uses the real API and cancels only this test's synthetic reservations.
test("doctor appointments book, persist, reject overlaps, cancel, and expose timezone-correct assistant proposals", async ({
  page,
}) => {
  test.skip(
    !process.env.E2E_PASSWORD,
    "Set E2E_PASSWORD for the local seeded instance.",
  );
  const failures: string[] = [];
  page.on("pageerror", (error) => failures.push(error.message));
  await page.goto("/app/doctors");
  const login = page.locator("form").filter({
    has: page.getByRole("button", { name: "Sign in", exact: true }),
  });
  await login
    .getByPlaceholder("Enter your username", { exact: true })
    .fill(process.env.E2E_USERNAME || "staff");
  await login
    .getByPlaceholder("Enter your password", { exact: true })
    .fill(process.env.E2E_PASSWORD!);
  await login.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("link", { name: "Doctors", exact: true }).click();
  const doctorResponse = await page.request.get(
    "/api/v1/doctors?size=100&active=true",
  );
  expect(doctorResponse.ok()).toBeTruthy();
  const doctors = (await doctorResponse.json()).items;
  const doctor =
    doctors.find(
      (d: { firstName: string; lastName: string }) =>
        d.firstName === "Elena" && d.lastName === "Dimitrova",
    ) || doctors[0];
  expect(doctor).toBeTruthy();
  const doctorRow = page
    .locator("tbody tr")
    .filter({ hasText: doctor.doctorIdentifier });
  await doctorRow
    .getByRole("button", { name: "Appointments", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Doctor appointments", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("combobox", { name: "Doctor", exact: true }),
  ).toHaveValue(String(doctor.id));

  const csrf = await (await page.request.get("/api/v1/auth/csrf")).json();
  const headers = { [csrf.headerName]: csrf.token };
  const created: number[] = [];
  const name = `Appointment verification ${Date.now()}`;
  const startsAt = `${new Date().getFullYear() + 2}-11-02T16:${String(Math.floor(Math.random() * 60)).padStart(2, "0")}`;
  const booking = page.getByRole("region", {
    name: "Book an appointment",
    exact: true,
  });
  try {
    await booking.getByLabel("Full name", { exact: true }).fill(name);
    await booking.locator('input[type="datetime-local"]').fill(startsAt);
    await booking.getByLabel("Duration (minutes)").fill("30");
    await booking
      .getByRole("button", { name: "Check availability", exact: true })
      .click();
    await expect(booking.getByRole("status")).toContainText("Available:");
    const save = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/v1/appointments") &&
        r.request().method() === "POST",
    );
    await booking
      .getByRole("button", { name: "Book appointment", exact: true })
      .click();
    const savedResponse = await save;
    expect(savedResponse.status()).toBe(201);
    const saved = await savedResponse.json();
    created.push(saved.id);
    expect(saved.attendeeName).toBe(name);
    expect(saved.durationMinutes).toBe(30);
    await expect(
      page.locator(".appointment-feedback").getByRole("status"),
    ).toContainText(`Appointment #${saved.id} booked`);
    await expect(
      page.locator(".appointment-schedule tbody tr").filter({ hasText: name }),
    ).toBeVisible();
    await page.reload();
    await expect(
      page.locator(".appointment-schedule tbody tr").filter({ hasText: name }),
    ).toBeVisible();
    await expect(page.locator(".appointment-schedule")).toHaveCSS(
      "opacity",
      "1",
    );
    await page.screenshot({
      path: "test-results/appointments-desktop.png",
      fullPage: true,
    });

    // Read-only availability never reserves; attempting an overlap must fail visibly.
    await booking
      .getByLabel("Full name", { exact: true })
      .fill(`${name} overlap`);
    await booking.locator('input[type="datetime-local"]').fill(startsAt);
    await booking
      .getByRole("button", { name: "Check availability", exact: true })
      .click();
    await expect(booking.getByRole("status")).toContainText("Unavailable:");
    await booking
      .getByRole("button", { name: "Book appointment", exact: true })
      .click();
    await expect(
      page.locator(".appointment-feedback").getByRole("alert"),
    ).toContainText("already has an appointment");
    await expect(
      page.locator(".appointment-feedback").getByRole("status"),
    ).toHaveCount(0);

    const row = page
      .locator(".appointment-schedule tbody tr")
      .filter({ hasText: name })
      .first();
    await row
      .getByRole("button", { name: "Cancel appointment", exact: true })
      .click();
    await row
      .getByRole("button", { name: "Confirm cancellation", exact: true })
      .click();
    await expect(
      page.locator(".appointment-feedback").getByRole("status"),
    ).toContainText(`Appointment #${saved.id} for ${name} cancelled`);
    await expect(
      page.locator(".appointment-schedule tbody tr").filter({ hasText: name }),
    ).toHaveCount(0);
    await page
      .getByRole("combobox", { name: "Status", exact: true })
      .selectOption("CANCELLED");
    await expect(
      page.locator(".appointment-schedule tbody tr").filter({ hasText: name }),
    ).toContainText("CANCELLED");
    await booking
      .getByRole("button", { name: "Check availability", exact: true })
      .click();
    await expect(booking.getByRole("status")).toContainText("Available:");

    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({
      path: "test-results/appointments-mobile.png",
      fullPage: true,
    });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBeTruthy();

    // Render and confirm through the actual assistant UI, including its response schema.
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.locator(".assistant-launch").click();
    const assistant = page.getByRole("dialog");
    await assistant
      .getByLabel("Assistant message", { exact: true })
      .fill(
        `Book appointment with ${doctor.doctorIdentifier} on ${startsAt} for ${name} assistant`,
      );
    await assistant
      .getByRole("button", { name: "Send message", exact: true })
      .click();
    await expect(assistant.locator(".confirmation")).toContainText(
      `${name} assistant`,
    );
    await expect(assistant.locator(".confirmation")).toContainText(
      "30 minutes",
    );
    await page.screenshot({
      path: "test-results/appointments-assistant.png",
      fullPage: true,
    });
    const confirming = page.waitForResponse(
      (r) =>
        /\/ai-actions\/\d+\/confirm$/.test(r.url()) &&
        r.request().method() === "POST",
    );
    await assistant
      .getByRole("button", { name: "Confirm appointment", exact: true })
      .click();
    const confirmed = await confirming;
    expect(confirmed.ok()).toBeTruthy();
    created.push((await confirmed.json()).id);
    await expect(assistant.locator(".confirmation")).toContainText("EXECUTED");
    expect(failures).toEqual([]);
  } finally {
    for (const id of created) {
      const list = await page.request.get(
        `/api/v1/appointments?doctorId=${doctor.id}&status=ALL&size=100`,
      );
      const appointment = (await list.json()).appointments.items.find(
        (a: { id: number }) => a.id === id,
      );
      if (appointment && appointment.status !== "CANCELLED") {
        const response = await page.request.post(
          `/api/v1/appointments/${id}/cancel`,
          { headers, data: { version: appointment.version } },
        );
        expect(response.ok()).toBeTruthy();
      }
    }
  }
});
