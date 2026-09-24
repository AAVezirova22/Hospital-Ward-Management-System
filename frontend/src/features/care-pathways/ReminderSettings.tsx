"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, activeDepartment } from "../../api";
import { ErrorBox } from "../../components/workspace";

type Preferences = {
  optedIn: boolean;
  timeZone: string;
  minutesBefore: number;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  operationalAlerts: boolean;
  pushAvailable: boolean;
  activeSubscriptions: number;
  availableLeadMinutes: number[];
};

function keyBytes(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const decoded = atob(
    normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="),
  );
  const buffer = new ArrayBuffer(decoded.length);
  const bytes = new Uint8Array(buffer);
  for (let index = 0; index < decoded.length; index++)
    bytes[index] = decoded.charCodeAt(index);
  return bytes;
}

export function ReminderSettings() {
  const department = activeDepartment();
  const client = useQueryClient();
  const preferences = useQuery({
    queryKey: ["task-reminder-preferences", department],
    queryFn: () => api<Preferences>("/task-reminders/preferences"),
  });
  const [form, setForm] = useState<Preferences | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [notice, setNotice] = useState("");
  const current = form ?? preferences.data;
  const browserAvailable =
    typeof window !== "undefined" &&
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    "Notification" in window &&
    window.isSecureContext;

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      await action();
      await client.invalidateQueries({
        queryKey: ["task-reminder-preferences"],
      });
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusy(false);
    }
  }

  async function enable() {
    if (!current || !browserAvailable) return;
    await run(async () => {
      const permission = await Notification.requestPermission();
      if (permission !== "granted")
        throw new Error(
          "Phone notifications were not allowed. You can still see care tasks in the app.",
        );
      const registration = await navigator.serviceWorker.register(
        "/care-reminders-sw.js",
        { scope: "/" },
      );
      const key = await api<{ publicKey: string | null }>(
        "/task-reminders/vapid-public-key",
      );
      if (!key.publicKey)
        throw new Error(
          "Phone reminders are unavailable for this department service.",
        );
      const existing = await registration.pushManager.getSubscription();
      const subscription =
        existing ??
        (await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: keyBytes(key.publicKey),
        }));
      const data = subscription.toJSON();
      if (!data.endpoint || !data.keys?.p256dh || !data.keys?.auth)
        throw new Error(
          "This browser returned an incomplete push subscription.",
        );
      await api("/task-reminders/subscriptions", "POST", {
        endpoint: data.endpoint,
        p256dh: data.keys.p256dh,
        auth: data.keys.auth,
      });
      await api("/task-reminders/preferences", "PUT", {
        optedIn: true,
        timeZone: current.timeZone,
        minutesBefore: current.minutesBefore,
        quietHoursStart: current.quietHoursStart || null,
        quietHoursEnd: current.quietHoursEnd || null,
        operationalAlerts: current.operationalAlerts,
      });
      setForm(null);
      setNotice("Private task reminders are enabled on this device.");
    });
  }

  async function disable() {
    if (!current) return;
    await run(async () => {
      await api("/task-reminders/preferences", "PUT", {
        optedIn: false,
        timeZone: current.timeZone,
        minutesBefore: current.minutesBefore,
        quietHoursStart: current.quietHoursStart || null,
        quietHoursEnd: current.quietHoursEnd || null,
        operationalAlerts: current.operationalAlerts,
      });
      await api("/task-reminders/subscriptions", "DELETE");
      const registration = await navigator.serviceWorker?.getRegistration("/");
      await (await registration?.pushManager.getSubscription())?.unsubscribe();
      setForm(null);
      setNotice("Task reminders are off. Existing tasks remain in the app.");
    });
  }

  async function saveTiming() {
    if (!current) return;
    await run(async () => {
      await api("/task-reminders/preferences", "PUT", {
        optedIn: current.optedIn,
        timeZone: current.timeZone,
        minutesBefore: current.minutesBefore,
        quietHoursStart: current.quietHoursStart || null,
        quietHoursEnd: current.quietHoursEnd || null,
        operationalAlerts: current.operationalAlerts,
      });
      setForm(null);
      setNotice("Reminder timing saved.");
    });
  }

  return (
    <details className="panel care-reminder-settings">
      <summary>
        Phone reminder settings {current?.optedIn ? "· On" : "· Off"}
      </summary>
      {preferences.isLoading && <p>Checking reminder settings…</p>}
      <ErrorBox error={preferences.error || error} />
      {current && (
        <>
          <p>
            Lock-screen alerts show only a generic task reminder. Sign in to see
            the patient or task.
          </p>
          {!current.pushAvailable && (
            <p role="status">
              Push delivery is not configured. Care tasks remain available here.
            </p>
          )}
          {!browserAvailable && (
            <p role="status">
              This browser or connection does not support secure push
              notifications.
            </p>
          )}
          <div className="care-reminder-grid">
            <label>
              Time zone
              <input
                value={current.timeZone}
                onChange={(event) =>
                  setForm({ ...current, timeZone: event.target.value })
                }
                list="care-timezones"
              />
            </label>
            <datalist id="care-timezones">
              <option
                value={Intl.DateTimeFormat().resolvedOptions().timeZone}
              />
            </datalist>
            <label>
              Notify before due
              <select
                value={current.minutesBefore}
                onChange={(event) =>
                  setForm({
                    ...current,
                    minutesBefore: Number(event.target.value),
                  })
                }
              >
                {current.availableLeadMinutes.map((minutes) => (
                  <option key={minutes} value={minutes}>
                    {minutes === 0 ? "At due time" : `${minutes} minutes`}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Quiet hours start
              <input
                type="time"
                value={current.quietHoursStart ?? ""}
                onChange={(event) =>
                  setForm({
                    ...current,
                    quietHoursStart: event.target.value || null,
                  })
                }
              />
            </label>
            <label>
              Quiet hours end
              <input
                type="time"
                value={current.quietHoursEnd ?? ""}
                onChange={(event) =>
                  setForm({
                    ...current,
                    quietHoursEnd: event.target.value || null,
                  })
                }
              />
            </label>
          </div>
          <label className="care-approval">
            <input
              type="checkbox"
              checked={current.operationalAlerts}
              onChange={(event) =>
                setForm({ ...current, operationalAlerts: event.target.checked })
              }
            />
            Also send generic department alerts to this device
          </label>
          <div className="care-editor-actions">
            <button
              className="secondary"
              disabled={
                busy || !!current.quietHoursStart !== !!current.quietHoursEnd
              }
              onClick={() => void saveTiming()}
            >
              Save timing
            </button>
            {current.optedIn ? (
              <button
                className="secondary"
                disabled={busy}
                onClick={() => void disable()}
              >
                Turn off reminders
              </button>
            ) : (
              <button
                className="primary"
                disabled={
                  busy ||
                  !browserAvailable ||
                  !current.pushAvailable ||
                  !!current.quietHoursStart !== !!current.quietHoursEnd
                }
                onClick={() => void enable()}
              >
                Enable on this device
              </button>
            )}
          </div>
          <small>
            {current.activeSubscriptions} active device
            {current.activeSubscriptions === 1 ? "" : "s"}
          </small>
        </>
      )}
      {notice && <p role="status">{notice}</p>}
    </details>
  );
}
