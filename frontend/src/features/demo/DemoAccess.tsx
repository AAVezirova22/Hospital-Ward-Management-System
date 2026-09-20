"use client";
import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, token, type User } from "../../api";

export function DemoAccess({ onLogin }: { onLogin: (u: User) => void }) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const status = useQuery({
    queryKey: ["demo-status"],
    queryFn: () => api<{ enabled: boolean }>("/demo/status"),
    retry: 3,
    retryDelay: 3000,
  });
  if (!status.data?.enabled) return null;
  return (
    <div className="demo-access">
      <span className="eyebrow">DEMO ENVIRONMENT</span>
      <p>Explore with synthetic records.</p>
      {(
        [
          ["ADMIN", "Enter demo as administrator"],
          ["DOCTOR", "View as doctor"],
          ["MEDICAL_STAFF", "View as medical staff"],
        ] as const
      ).map(([role, label]) => (
        <button
          key={role}
          type="button"
          className="secondary"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError("");
            try {
              await api<User>("/demo/login", "POST", { role });
              await token();
              onLogin(await api<User>("/auth/me"));
            } catch (e) {
              setError((e as Error).message);
            } finally {
              setBusy(false);
            }
          }}
        >
          {label}
        </button>
      ))}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
export function DemoReset({ user }: { user: User }) {
  const client = useQueryClient(),
    [open, setOpen] = useState(false),
    [confirmation, setConfirmation] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const status = useQuery({
    queryKey: ["demo-status"],
    queryFn: () => api<{ enabled: boolean }>("/demo/status"),
  });
  if (!status.data?.enabled) return null;
  return (
    <div className="demo-marker">
      <span>DEMO ENVIRONMENT</span>
      {user.role === "ADMIN" && (
        <button className="text-button" onClick={() => setOpen(!open)}>
          Reset demonstration
        </button>
      )}
      {open && (
        <div className="panel reset-panel">
          <h3>Restore the demonstration?</h3>
          <p>
            This deletes all records and accounts in this demo database,
            recreates the scenario, and signs everyone out. This cannot be
            undone.
          </p>
          <label>
            Type RESET DEMO
            <input
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
            />
          </label>
          {error && <p role="alert">{error}</p>}
          <button
            className="secondary"
            disabled={busy}
            onClick={() => setOpen(false)}
          >
            Cancel
          </button>
          <button
            className="primary"
            disabled={confirmation !== "RESET DEMO" || busy}
            onClick={async () => {
              setBusy(true);
              try {
                await api("/demo/reset", "POST", { confirmation });
                client.clear();
                window.dispatchEvent(new Event("session-expired"));
              } catch (e) {
                setError((e as Error).message);
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Restoring…" : "Restore demo"}
          </button>
        </div>
      )}
    </div>
  );
}
export function WakeScreen({ onReady }: { onReady: () => void }) {
  const [attempt, setAttempt] = useState(0),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    let stopped = false,
      tries = 0;
    async function check() {
      try {
        const response = await fetch("/api/v1/health", {
          signal: AbortSignal.any([
            controller.signal,
            AbortSignal.timeout(10000),
          ]),
          cache: "no-store",
        });
        if (response.ok && (await response.json()).status === "UP") {
          if (!stopped) onReady();
          return;
        }
      } catch {}
      if (!stopped) {
        setAttempt(++tries);
        if (tries < 12) timer = setTimeout(check, 3000);
      }
    }
    check();
    return () => {
      stopped = true;
      controller.abort();
      clearTimeout(timer);
    };
  }, [onReady, retry]);
  return (
    <div className="boot">
      <div className="wake-pulse" />
      <h1>Waking the hospital workspace…</h1>
      <p>
        {attempt < 12
          ? "Waiting for the department service to become available."
          : "The service is taking longer than expected."}
      </p>
      {attempt >= 12 && (
        <button
          className="primary"
          onClick={() => {
            setAttempt(0);
            setRetry((n) => n + 1);
          }}
        >
          Try again
        </button>
      )}
    </div>
  );
}
