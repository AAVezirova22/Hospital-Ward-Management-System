"use client";
import { useEffect, useState } from "react";
import { api } from "../api";
import { Modal } from "./workspace";

const SESSION_MS = 30 * 60 * 1000;
const WARN_MS = 28 * 60 * 1000;

export function IdleTimeout() {
  const [warn, setWarn] = useState(false);
  useEffect(() => {
    let last = Date.now();
    const bump = () => {
      last = Date.now();
      setWarn(false);
    };
    const events = ["click", "keydown", "pointerdown", "scroll"] as const;
    events.forEach((name) => window.addEventListener(name, bump));
    const timer = window.setInterval(() => {
      const idle = Date.now() - last;
      if (idle >= SESSION_MS) window.dispatchEvent(new Event("session-expired"));
      else if (idle >= WARN_MS) setWarn(true);
    }, 1000);
    return () => {
      window.clearInterval(timer);
      events.forEach((name) => window.removeEventListener(name, bump));
    };
  }, []);
  if (!warn) return null;
  return (
    <Modal title="Session about to expire" onClose={() => setWarn(false)}>
      <p>You have been idle. Stay signed in to keep this admission form, or you will be signed out in two minutes.</p>
      <div className="actions">
        <button
          className="primary"
          onClick={async () => {
            await api("/auth/me");
            setWarn(false);
          }}
        >
          Stay signed in
        </button>
      </div>
    </Modal>
  );
}
