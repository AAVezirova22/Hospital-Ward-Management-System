"use client";
import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BedDouble, Building2, ChevronDown, Copy, KeyRound, Plus } from "lucide-react";
import {
  api,
  activeDepartment,
  setActiveDepartment,
} from "../api";
import type { WorkspaceHospital, WorkspaceList } from "../api/contracts";
import { ErrorBox, Modal } from "./workspace";

function currentNames(data: WorkspaceList | undefined) {
  const id = Number(activeDepartment() || data?.activeDepartmentId || 0);
  for (const hospital of data?.hospitals ?? []) {
    const department = hospital.departments.find((item) => item.id === id);
    if (department) return { hospital, department };
  }
  return {
    hospital: data?.hospitals[0],
    department: data?.hospitals[0]?.departments[0],
  };
}

async function copyCode(value: string) {
  try {
    await navigator.clipboard.writeText(value);
    window.dispatchEvent(
      new CustomEvent("saved", { detail: "Join code copied" }),
    );
  } catch {}
}

export function WorkspaceSwitcher() {
  const client = useQueryClient();
  const { data, error, isLoading } = useQuery<WorkspaceList>({
    queryKey: ["/workspaces", activeDepartment()],
    queryFn: () => api("/workspaces"),
  });
  const [open, setOpen] = useState(false);
  const [panel, setPanel] = useState<"list" | "join" | "hospital" | "department">(
    "list",
  );
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<Error | null>(null);
  const [joinCode, setJoinCode] = useState("");
  const [hospitalName, setHospitalName] = useState("");
  const [departmentName, setDepartmentName] = useState("");
  const [hostHospital, setHostHospital] = useState<WorkspaceHospital>();
  const current = useMemo(() => currentNames(data), [data]);

  const refresh = async (id?: number) => {
    if (id) setActiveDepartment(id);
    await client.invalidateQueries();
  };

  const openDepartment = async (id: number) => {
    await refresh(id);
    setOpen(false);
  };

  const submit = async (run: () => Promise<{ departmentId?: number }>) => {
    setBusy(true);
    setFormError(null);
    try {
      const result = await run();
      setPanel("list");
      await refresh(result.departmentId);
    } catch (e) {
      setFormError(e as Error);
    } finally {
      setBusy(false);
    }
  };

  const close = () => {
    setOpen(false);
    setPanel("list");
    setFormError(null);
  };

  const rotate = async (hospital: boolean, id: number) => {
    setBusy(true);
    setFormError(null);
    try {
      const path = hospital
        ? `/workspaces/hospitals/${id}/code`
        : `/workspaces/departments/${id}/code`;
      const result = await api<{ code: string }>(path, "POST");
      await client.invalidateQueries({ queryKey: ["/workspaces"] });
      if (result.code) await copyCode(result.code);
    } catch (e) {
      setFormError(e as Error);
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
    <button
      type="button"
      className="department workspace-switcher"
      aria-haspopup="dialog"
      aria-expanded={open}
      onClick={() => setOpen(true)}
    >
      <BedDouble size={19} strokeWidth={1.5} />
      <div>
        {current.hospital?.name || "Hospital"}
        <small>{current.department?.name || "Choose a department"}</small>
      </div>
      <ChevronDown size={16} aria-hidden="true" />
    </button>
    {open && (
      <Modal title="Hospitals and departments" onClose={close}>
        <ErrorBox error={error || formError} />
        {isLoading && <p>Loading workspaces…</p>}
        {panel === "list" && (
          <div className="workspace-list">
            {(data?.hospitals ?? []).map((hospital) => (
              <section key={hospital.id}>
                <header>
                  <Building2 size={16} />
                  <strong>{hospital.name}</strong>
                  {hospital.owner && <span>Owner</span>}
                </header>
                {hospital.departments.length === 0 && (
                  <p className="muted">
                    Join a department with a code to open its records.
                  </p>
                )}
                <ul>
                  {hospital.departments.map((department) => (
                    <li key={department.id}>
                      <button
                        type="button"
                        className={
                          department.id === current.department?.id
                            ? "selected"
                            : "secondary"
                        }
                        onClick={() => openDepartment(department.id)}
                      >
                        {department.name}
                        <small>{department.role.replaceAll("_", " ").toLowerCase()}</small>
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}
      </Modal>
    )}
    </>
  );
}
