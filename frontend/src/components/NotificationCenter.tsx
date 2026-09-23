"use client";
import { useEffect, useState } from "react";
import { Bell } from "../icons";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { api, allPages, activeDepartment } from "../api";
import type {
  DischargeReminderOutcome,
  RoomCapacity,
  OperationsReport,
} from "../api/contracts";
import { Modal } from "./workspace";
export function NotificationCenter() {
  const [open, setOpen] = useState(false),
    [notices, setNotices] = useState<string[]>([]);
  const rooms = useQuery({
    queryKey: ["/rooms", activeDepartment()],
    queryFn: () => allPages<RoomCapacity>("/rooms"),
    refetchInterval: 30000,
  });
  const operations = useQuery({
    queryKey: ["/reports/operations", activeDepartment()],
    queryFn: () => api<OperationsReport>("/reports/operations"),
    refetchInterval: 30000,
  });
  const reminders = useQuery({
    queryKey: ["/reports/discharge-reminders", activeDepartment()],
    queryFn: () =>
      api<DischargeReminderOutcome[]>("/reports/discharge-reminders"),
    refetchInterval: 30000,
  });
  useEffect(() => {
    const handler = (e: Event) =>
      setNotices((n) => [String((e as CustomEvent).detail), ...n].slice(0, 6));
    window.addEventListener("saved", handler);
    return () => window.removeEventListener("saved", handler);
  }, []);
  const full = (rooms.data ?? []).filter(
    (r) => r.active && r.availableBeds === 0,
  );
  const reminderAttention = (reminders.data ?? []).filter(
    (r) => r.status === "FAILED" || r.status === "NO_RECIPIENT",
  );
  const alertCount = full.length + reminderAttention.length;
  return (
    <div className="notification-center">
      <button
        className="icon"
        aria-label={"Notifications (" + (alertCount + notices.length) + ")"}
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => setOpen(!open)}
      >
        <Bell size={18} />
        {alertCount > 0 && (
          <span className="notification-count">{alertCount}</span>
        )}
      </button>
      {open && (
        <Modal title="Operations alerts" onClose={() => setOpen(false)}>
          {(rooms.error || operations.error || reminders.error) && (
            <p role="alert">
              Alerts could not be refreshed. Displayed information may be
              outdated.
            </p>
          )}
          {full.map((r) => (
            <p className="capacity-alert" key={r.id}>
              <Link href="/app/planner" onClick={() => setOpen(false)}>
                Room {r.roomNumber} is at full capacity →
              </Link>
              <small>
                {r.occupiedBeds}/{r.bedCount} occupied · observed{" "}
                {new Date(rooms.dataUpdatedAt).toLocaleTimeString([], {
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </small>
            </p>
          ))}
          {operations.data && (
            <p className="expected-discharge">
              <Link href="/app/planner" onClick={() => setOpen(false)}>
                {operations.data.expectedDischargesToday} expected discharges
                today
              </Link>
              <small>Scheduled dates use {operations.data.timeZone}</small>
            </p>
          )}
          {(reminders.data ?? []).map((reminder) => (
            <p
              className={
                reminder.status === "FAILED" ||
                reminder.status === "NO_RECIPIENT"
                  ? "capacity-alert"
                  : "expected-discharge"
              }
              key={reminder.id}
            >
              Discharge reminder for {reminder.expectedDischargeDate} (
              {reminder.windowDays} day{reminder.windowDays === 1 ? "" : "s"}{" "}
              before)
              <small>
                {reminder.status === "ACCEPTED"
                  ? "Accepted by the email provider"
                  : reminder.status === "NO_RECIPIENT"
                    ? "No verified team email is available"
                    : reminder.status === "FAILED"
                      ? "Delivery failed (" + reminder.attemptCount + " attempts)"
                      : reminder.status === "CANCELLED"
                        ? "Cancelled because the admission plan changed"
                        : reminder.status === "SENDING"
                          ? "Sending"
                          : "Waiting to send"}
              </small>
            </p>
          ))}
          {rooms.data && (
            <p>
              {rooms.data
                .filter((r) => r.active)
                .reduce((sum, r) => sum + r.availableBeds, 0)}{" "}
              beds available<small>Current department capacity</small>
            </p>
          )}
          {notices.map((n, i) => (
            <p key={i}>{n}</p>
          ))}
          {!full.length &&
            !notices.length &&
            !(reminders.data ?? []).length && (
              <p>No capacity warnings, discharge reminder outcomes, or recent actions.</p>
            )}
          <button
            className="text-button"
            onClick={() => {
              setNotices([]);
              setOpen(false);
            }}
          >
            Clear recent actions
          </button>
        </Modal>
      )}
    </div>
  );
}
