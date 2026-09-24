self.addEventListener("push", (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    payload = {};
  }
  const operational = payload.type === "OPERATIONAL_ALERT";
  const token =
    typeof payload.reminderToken === "string" &&
    /^[0-9a-f-]{36}$/i.test(payload.reminderToken)
      ? payload.reminderToken
      : null;
  const notificationToken =
    typeof payload.notificationToken === "string" &&
    /^[0-9a-f-]{36}$/i.test(payload.notificationToken)
      ? payload.notificationToken
      : null;
  const path = operational
    ? notificationToken
      ? `/app/dashboard?notification=${encodeURIComponent(notificationToken)}`
      : "/app/dashboard"
    : token
      ? `/app/tasks?reminder=${encodeURIComponent(token)}`
      : "/app/tasks";
  event.waitUntil(
    self.registration.showNotification(
      operational ? "Department alert" : "Care task reminder",
      {
        body: operational
          ? "You have a department alert to review. Sign in to view it."
          : "You have a care task to review. Sign in to view it.",
        tag: operational
          ? notificationToken
            ? `department-alert-${notificationToken}`
            : "department-alert"
          : token
            ? `care-task-${token}`
            : "care-task",
      data: { path },
      },
    ),
  );
});
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const path = event.notification.data?.path || "/app/tasks";
  event.waitUntil(
    self.clients
      .matchAll({ type: "window", includeUncontrolled: true })
      .then(async (windows) => {
        for (const window of windows) {
          if (new URL(window.url).origin === self.location.origin) {
            await window.navigate(path);
            return window.focus();
          }
        }
        return self.clients.openWindow(path);
      }),
  );
});
