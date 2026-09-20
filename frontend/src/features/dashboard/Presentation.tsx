"use client";
import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import { Link, useUser } from "../../components/workspace";
import { OperationsOverview } from "./OperationsOverview";
export function Presentation() {
  const user = useUser(),
    canvas = useRef<HTMLCanvasElement>(null),
    [url, setUrl] = useState("");
  useEffect(() => {
    const link = new URL("/app/dashboard", window.location.origin).href;
    setUrl(link);
    const theme = document.documentElement.getAttribute("data-theme");
    const styles = getComputedStyle(document.documentElement);
    const dark =
      styles.getPropertyValue("--ink").trim() ||
      styles.getPropertyValue("--text").trim() ||
      (theme === "light" ? "#13251c" : "#e7eee6");
    const light =
      styles.getPropertyValue("--panel").trim() ||
      (theme === "light" ? "#f8faf6" : "#122226");
    if (canvas.current)
      QRCode.toCanvas(canvas.current, link, {
        width: 100,
        margin: 2,
        color: { dark: dark || "#13251c", light: light || "#f8faf6" },
      }).catch(() => {});
  }, []);
  return (
    <>
      <div className="presentation-toolbar">
        <div>
          <h1>Medcore · Live ward</h1>
          <p>
            Current operations ·{" "}
            {user.role === "DOCTOR" ? "Assigned patient scope" : "Department"}
          </p>
        </div>
        <div className="presentation-qr">
          <canvas
            ref={canvas}
            role="img"
            aria-label="QR code linking to this workspace"
          />
          <a href={url}>Open on your phone</a>
        </div>
        <div className="actions">
          <button
            className="secondary"
            onClick={() => {
              if (document.fullscreenElement) document.exitFullscreen();
              else document.documentElement.requestFullscreen().catch(() => {});
            }}
          >
            Toggle full screen
          </button>
          <Link to="/app/dashboard">Exit presentation</Link>
        </div>
      </div>
      <OperationsOverview presentation />
    </>
  );
}
