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
                      {department.joinCode && (
                        <p className="workspace-code">
                          Department code {department.joinCode}
                          <button
                            type="button"
                            className="icon"
                            aria-label="Copy department join code"
                            onClick={() => copyCode(department.joinCode!)}
                          >
                            <Copy size={14} />
                          </button>
                          <button
                            type="button"
                            className="text-button"
                            disabled={busy}
                            onClick={() => rotate(false, department.id)}
                          >
                            Replace
                          </button>
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
                {hospital.owner && hospital.joinCode && (
                  <p className="workspace-code">
                    Hospital code {hospital.joinCode}
                    <button
                      type="button"
                      className="icon"
                      aria-label="Copy hospital join code"
                      onClick={() => copyCode(hospital.joinCode!)}
                    >
                      <Copy size={14} />
                    </button>
                    <button
                      type="button"
                      className="text-button"
                      disabled={busy}
                      onClick={() => rotate(true, hospital.id)}
                    >
                      Replace
                    </button>
                  </p>
                )}
                {hospital.owner && (
                  <button
                    type="button"
                    className="text-button"
                    onClick={() => {
                      setHostHospital(hospital);
                      setDepartmentName("");
                      setPanel("department");
                    }}
                  >
                    <Plus size={14} />
                    New department
                  </button>
                )}
              </section>
            ))}
            <div className="workspace-actions">
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setJoinCode("");
                  setPanel("join");
                }}
              >
                <KeyRound size={16} />
                Join with a code
              </button>
              <button
                type="button"
                className="primary"
                onClick={() => {
                  setHospitalName("");
                  setDepartmentName("");
                  setPanel("hospital");
                }}
              >
                <Plus size={16} />
                Create a hospital
              </button>
            </div>
          </div>
        )}
        {panel === "join" && (
          <form
            className="workspace-form"
            onSubmit={(e) => {
              e.preventDefault();
              void submit(() =>
                api("/workspaces/join", "POST", { code: joinCode }),
              );
            }}
          >
            <p>
              A hospital code adds hospital membership. A department code opens
              that department as medical staff, never as an administrator.
            </p>
            <label>
              Join code
              <input
                autoFocus
                value={joinCode}
                onChange={(e) => setJoinCode(e.target.value)}
                placeholder="H- or D- code"
                autoComplete="off"
              />
            </label>
            <div className="actions">
              <button type="button" className="secondary" onClick={() => setPanel("list")}>
                Back
              </button>
              <button className="primary" disabled={busy || !joinCode.trim()}>
                {busy ? "Joining…" : "Join"}
              </button>
            </div>
          </form>
        )}
        {panel === "hospital" && (
          <form
            className="workspace-form"
            onSubmit={(e) => {
              e.preventDefault();
              void submit(() =>
                api("/workspaces/hospitals", "POST", {
                  name: hospitalName,
                  departmentName,
                }),
              );
            }}
          >
            <p>You become the owner of the hospital and administrator of its first department.</p>
            <label>
              Hospital name
              <input
                autoFocus
                value={hospitalName}
                onChange={(e) => setHospitalName(e.target.value)}
                maxLength={120}
              />
            </label>
            <label>
              First department
              <input
                value={departmentName}
                onChange={(e) => setDepartmentName(e.target.value)}
                maxLength={120}
              />
            </label>
            <div className="actions">
              <button type="button" className="secondary" onClick={() => setPanel("list")}>
                Back
              </button>
              <button
                className="primary"
                disabled={busy || !hospitalName.trim() || !departmentName.trim()}
              >
                {busy ? "Creating…" : "Create hospital"}
              </button>
            </div>
          </form>
        )}
        {panel === "department" && hostHospital && (
          <form
            className="workspace-form"
            onSubmit={(e) => {
              e.preventDefault();
              void submit(() =>
                api(
                  `/workspaces/hospitals/${hostHospital.id}/departments`,
                  "POST",
                  { name: departmentName },
                ),
              );
            }}
          >
            <p>This department is added to {hostHospital.name}. You administer it.</p>
            <label>
              Department name
              <input
                autoFocus
                value={departmentName}
                onChange={(e) => setDepartmentName(e.target.value)}
                maxLength={120}
              />
            </label>
            <div className="actions">
              <button type="button" className="secondary" onClick={() => setPanel("list")}>
                Back
              </button>
              <button className="primary" disabled={busy || !departmentName.trim()}>
                {busy ? "Creating…" : "Create department"}
              </button>
            </div>
          </form>
        )}
      </Modal>
    )}
    </>
  );
}
