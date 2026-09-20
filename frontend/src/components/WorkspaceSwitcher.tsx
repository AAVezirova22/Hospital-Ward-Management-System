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
  return (
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
  );
}
