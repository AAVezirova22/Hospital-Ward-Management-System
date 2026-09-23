"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Users,
  BedDouble,
  Sparkles,
  Plus,
  X,
} from "../icons";
import { useUser, Modal, useData, useAllPages, ErrorBox } from "./workspace";
import { fullName } from "../api";
import type { PatientDirectoryItem, PatientDirectoryPage, AdmissionView } from "../api/contracts";
import { Workflow } from "../features/admissions/Workflow";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

function QuickTask({ kind, close }: { kind: string; close: () => void }) {
  const patients = useData("/patients");
  const admissions = useAllPages<AdmissionView>("/admissions?status=ACTIVE");
  const [chosen, setChosen] = useState<Patient>();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const patients = useData(
    `/patients?q=${encodeURIComponent(search)}&page=${page}&size=20`,
  );
  const admissions = useData("/admissions");
  const directory = patients.data as PatientDirectoryPage | undefined;
  const rows = (admissions.data ?? []) as AdmissionView[];
  const active = (id: number) =>
    rows.find((v) => v.patient.id === id && v.admission.status === "ACTIVE");
  if (chosen)
    return (
      <Workflow
        kind={kind}
        patient={chosen}
        active={active(chosen.id)}
        onClose={close}
      />
    );
  return (
    <Modal title="Choose a patient" onClose={close}>
      <label>
        Find patient
        <input
          autoFocus
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          placeholder="Name or patient ID"
        />
      </label>
      <ErrorBox error={patients.error || admissions.error} />
      {patients.isLoading || admissions.isLoading ? (
        <p>Loading patients…</p>
      ) : (
        <div className="quick-patients">
          {!patients.error &&
            !admissions.error &&
            (directory?.items ?? [])
              .filter(
                (p) =>
                  (kind === "admit" ? !active(p.id) : !!active(p.id)) &&
                  `${fullName(p)} ${p.patientIdentifier}`
                    .toLowerCase()
                    .includes(search.toLowerCase()),
              )
              .map((p) => (
                <button
                  className="secondary"
                  key={p.id}
                  onClick={() => setChosen(p)}
                >
                  {fullName(p)}
                  <small>{p.patientIdentifier}</small>
                </button>
              ))}
        </div>
      )}
      {directory && directory.totalPages > 1 && (
        <div className="table-pagination">
          <span>Page {directory.page + 1} of {directory.totalPages}</span>
          <button
            className="secondary"
            disabled={directory.page === 0}
            onClick={() => setPage(directory.page - 1)}
          >
            Previous
          </button>
          <button
            className="secondary"
            disabled={!directory.hasNext}
            onClick={() => setPage(directory.nextPage ?? directory.page + 1)}
          >
            Next
          </button>
        </div>
      )}
      <p className="muted">
        Only patients eligible for this action are listed. Every change is
        reviewed before saving.
      </p>
    </Modal>
  );
}

export function MobileNavigation({ onAssistant }: { onAssistant: () => void }) {
  const pathname = usePathname(),
    user = useUser();
  const [open, setOpen] = useState(false),
    [task, setTask] = useState<string>();
  useEffect(() => {
    setOpen(false);
    setTask(undefined);
  }, [pathname]);
  useEffect(() => {
    const escape = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", escape);
    return () => window.removeEventListener("keydown", escape);
  }, []);
  return (
    <>
      <div className="mobile-workspace">
        <WorkspaceSwitcher />
      </div>
      <nav className="mobile-bottom-nav" aria-label="Quick navigation">
        {[
          ["dashboard", "Overview", LayoutDashboard],
          ["patients", "Patients", Users],
          ["planner", "Rooms", BedDouble],
        ].map(([route, label, Icon]) => {
          const Glyph = Icon as typeof Users;
          return (
            <Link
              key={String(route)}
              href={`/app/${route}`}
              aria-current={
                pathname.includes(String(route)) ? "page" : undefined
              }
            >
              <Glyph size={20} strokeWidth={1.5} />
              {String(label)}
            </Link>
          );
        })}
        <button aria-label="Ask operations assistant" onClick={onAssistant}>
          <Sparkles size={20} strokeWidth={1.5} />
          Assistant
        </button>
      </nav>
      <div className="mobile-quick-actions">
        {open && (
          <div className="quick-action-menu" id="quick-actions">
            <Link href="/app/patients">Find patient</Link>
            {user.role !== "DOCTOR" && (
              <>
                <button
                  onClick={() => {
                    setTask("admit");
                    setOpen(false);
                  }}
                >
                  Admit patient
                </button>
                <button
                  onClick={() => {
                    setTask("transfer");
                    setOpen(false);
                  }}
                >
                  Transfer patient
                </button>
              </>
            )}
            <button
              onClick={() => {
                setTask("procedure");
                setOpen(false);
              }}
            >
              Record procedure
            </button>
          </div>
        )}
        <button
          className="quick-action-toggle"
          aria-label={open ? "Close quick actions" : "Open quick actions"}
          aria-expanded={open}
          aria-controls="quick-actions"
          onClick={() => setOpen(!open)}
        >
          {open ? <X /> : <Plus />}
        </button>
      </div>
      {task && <QuickTask kind={task} close={() => setTask(undefined)} />}
    </>
  );
}
