"use client";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import {
  api,
  allPages,
  fullName,
  activeDepartment,
  patientHref,
  type User,
} from "../../api";
import type { Patient, RoomCapacity, Doctor } from "../../api/contracts";
export function CommandResults({
  query,
  onClose,
  user,
}: {
  query: string;
  onClose: () => void;
  user: User;
}) {
  const router = useRouter(),
    [selected, setSelected] = useState(-1);
  const patients = useQuery({
    queryKey: ["/patients", activeDepartment()],
    queryFn: () => api<Patient[]>("/patients"),
  });
  const rooms = useQuery({
    queryKey: ["/rooms", activeDepartment()],
    queryFn: () => allPages<RoomCapacity>("/rooms"),
  });
  const doctors = useQuery({
    queryKey: ["/doctors", activeDepartment()],
    queryFn: () => allPages<Doctor>("/doctors"),
  });
  const screens = [
    "dashboard",
    "patients",
    "admissions",
    "rooms",
    "planner",
    "doctors",
    "procedures",
    "reports",
    "presentation",
    ...(user.role === "ADMIN" ? ["users", "audit"] : []),
  ];
  const q = query.toLowerCase().trim();
  const candidates = [
    ...screens.map((route) => ({
      label: `Go to ${route === "dashboard" ? "overview" : route}`,
      detail: "Screen",
      href: `/app/${route}`,
    })),
    ...(patients.data ?? []).map((p) => ({
      label: fullName(p),
      detail: p.patientIdentifier,
      href: patientHref(p),
    })),
    ...(rooms.data ?? []).map((r) => ({
      label: `Room ${r.roomNumber}`,
      detail: r.active ? `${r.availableBeds} beds available` : "Inactive",
      href: `/app/rooms?room=${r.id}`,
    })),
    ...(doctors.data ?? []).map((d) => ({
      label: `Dr. ${fullName(d)}`,
      detail: d.specialty,
      href: `/app/doctors?q=${encodeURIComponent(fullName(d))}`,
    })),
  ];
  const matches = (
    q
      ? candidates.filter((c) =>
          `${c.label} ${c.detail}`.toLowerCase().includes(q),
        )
      : candidates.filter((c) =>
          ["/app/planner", "/app/patients", "/app/reports"].includes(c.href),
        )
  ).slice(0, 8);
  const signature = matches.map((m) => m.href).join(",");
  useEffect(() => setSelected(-1), [query, signature]);
  function open(index: number) {
    const item = matches[index];
    if (item) {
      router.push(item.href);
      onClose();
    }
  }
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.id !== "assistant-message") return;
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        setSelected((n) =>
          matches.length
            ? (n + (e.key === "ArrowDown" ? 1 : -1) + matches.length) %
              matches.length
            : -1,
        );
      }
      if (e.key === "Enter" && selected >= 0 && matches[selected]) {
        e.preventDefault();
        e.stopImmediatePropagation();
        open(selected);
      }
    };
    window.addEventListener("keydown", key, true);
    return () => window.removeEventListener("keydown", key, true);
  });
  return (
    <div className="command-results" role="listbox" aria-label="Search results">
      {matches.map((m, i) => (
        <button
          type="button"
          role="option"
          aria-selected={selected === i}
          key={m.href + m.label}
          onClick={() => open(i)}
        >
          <span>{m.label}</span>
          <small>{m.detail}</small>
        </button>
      ))}
      {q && !matches.length && (
        <p>No matching records. Press Enter to ask the assistant.</p>
      )}
      <small>
        ↑ ↓ to select · Enter to open · Type a request and send to ask the
        assistant
      </small>
      {(patients.error || rooms.error || doctors.error) && (
        <small>Some search sources could not load.</small>
      )}
    </div>
  );
}
