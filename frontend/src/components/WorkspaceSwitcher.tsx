"use client";
import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  BedDouble,
  Building2,
  ChevronDown,
  Copy,
  KeyRound,
  Plus,
} from "lucide-react";
import { api, activeDepartment, setActiveDepartment } from "../api";
import type { WorkspaceHospital, WorkspaceList } from "../api/contracts";
import { ErrorBox, Modal } from "./workspace";
import { currentNames } from "./workspace-names";

async function copyCode(value: string) {
  try {
    await navigator.clipboard.writeText(value);
    window.dispatchEvent(
      new CustomEvent("saved", { detail: "Join code copied" }),
    );
  } catch {}
}

async function revealAndCopy(hospital: boolean, id: number) {
  const path = hospital
    ? `/workspaces/hospitals/${id}/code`
    : `/workspaces/departments/${id}/code`;
  const result = await api<{ code: string }>(path);
  if (result.code) await copyCode(result.code);
}

export function WorkspaceSwitcher() {
  const client = useQueryClient();
  const { data, error, isLoading } = useQuery<WorkspaceList>({
    queryKey: ["/workspaces", activeDepartment()],
    queryFn: () => api("/workspaces"),
  });
  const [open, setOpen] = useState(false);
  const [panel, setPanel] = useState<
    "list" | "join" | "hospital" | "department"
  >("list");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<Error | null>(null);
  const [joinCode, setJoinCode] = useState("");
  const [joinHint, setJoinHint] = useState("");
  const [hospitalName, setHospitalName] = useState("");
  const [departmentName, setDepartmentName] = useState("");
  const [hostHospital, setHostHospital] = useState<WorkspaceHospital>();
  const current = useMemo(() => currentNames(data), [data]);
  useEffect(() => {
    if (data?.activeDepartmentId && !activeDepartment()) {
      setActiveDepartment(data.activeDepartmentId);
    }
  }, [data]);

  const refresh = async (id?: number) => {
    if (id) setActiveDepartment(id);
    await client.invalidateQueries();
  };

  const openDepartment = async (id: number) => {
    await refresh(id);
    setOpen(false);
  };

  const submit = async (
    run: () => Promise<{
      departmentId?: number;
      hospitalId?: number;
      hospitalName?: string;
    }>,
  ) => {
    setBusy(true);
    setFormError(null);
    setJoinHint("");
    try {
      const result = await run();
      if (result.departmentId) {
        setPanel("list");
        await refresh(result.departmentId);
      } else if (result.hospitalId && !result.departmentId) {
        setJoinCode("");
        setPanel("join");
        setJoinHint(
          `Joined ${result.hospitalName || "the hospital"}. Paste a department code to open its records.`,
        );
        await client.invalidateQueries({ queryKey: ["/workspaces"] });
      } else {
        setPanel("list");
        await refresh(result.departmentId);
      }
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

  const rotate = async (
    hospital: boolean,
    id: number,
    options?: { expiresInHours?: number; singleUse?: boolean },
  ) => {
    const oneTime = Boolean(options?.singleUse);
    if (
      !window.confirm(
        oneTime
          ? "Replace this join code with a 24-hour, one-time invite? The previous code will stop working immediately."
          : "Replace this join code? The previous code will stop working immediately.",
      )
    )
      return;
    setBusy(true);
    setFormError(null);
    try {
      const path = hospital
        ? `/workspaces/hospitals/${id}/code`
        : `/workspaces/departments/${id}/code`;
      const result = await api<{ code: string }>(path, "POST", {
        expiresInHours: options?.expiresInHours ?? null,
        singleUse: oneTime,
      });
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
        aria-label="Open hospital switcher"
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
                          <small>
                            {department.role.replaceAll("_", " ").toLowerCase()}
                          </small>
                        </button>
                        {department.hasJoinCode && (
                          <p className="workspace-code">
                            Department join code
                            <button
                              type="button"
                              className="icon"
                              aria-label="Copy department join code"
                              onClick={() =>
                                void revealAndCopy(false, department.id)
                              }
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
                            <button
                              type="button"
                              className="text-button"
                              disabled={busy}
                              onClick={() =>
                                rotate(false, department.id, {
                                  expiresInHours: 24,
                                  singleUse: true,
                                })
                              }
                            >
                              One-time 24h
                            </button>
                          </p>
                        )}
                        <button
                          type="button"
                          className="text-button"
                          disabled={busy}
                          onClick={() =>
                            void (async () => {
                              setBusy(true);
                              setFormError(null);
                              try {
                                await api(
                                  `/workspaces/departments/${department.id}/leave`,
                                  "POST",
                                  {},
                                );
                                if (
                                  Number(activeDepartment()) === department.id
                                )
                                  setActiveDepartment(null);
                                await client.invalidateQueries();
                              } catch (e) {
                                setFormError(e as Error);
                              } finally {
                                setBusy(false);
                              }
                            })()
                          }
                        >
                          Leave department
                        </button>
                      </li>
                    ))}
                  </ul>
                  {hospital.owner && hospital.hasJoinCode && (
                    <p className="workspace-code">
                      Hospital join code
                      <button
                        type="button"
                        className="icon"
                        aria-label="Copy hospital join code"
                        onClick={() => void revealAndCopy(true, hospital.id)}
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
                      <button
                        type="button"
                        className="text-button"
                        disabled={busy}
                        onClick={() =>
                          rotate(true, hospital.id, {
                            expiresInHours: 24,
                            singleUse: true,
                          })
                        }
                      >
                        One-time 24h
                      </button>
                    </p>
                  )}
                  <button
                    type="button"
                    className="text-button"
                    disabled={busy}
                    onClick={() =>
                      void (async () => {
                        setBusy(true);
                        setFormError(null);
                        try {
                          await api(
                            `/workspaces/hospitals/${hospital.id}/leave`,
                            "POST",
                            {},
                          );
                          setActiveDepartment(null);
                          await client.invalidateQueries();
                        } catch (e) {
                          setFormError(e as Error);
                        } finally {
                          setBusy(false);
                        }
                      })()
                    }
                  >
                    Leave hospital
                  </button>
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
                A hospital code adds hospital membership. A department code
                opens that department as medical staff, never as an
                administrator.
              </p>
              {joinHint && (
                <p className="muted" role="status">
                  {joinHint}
                </p>
              )}
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
                <button
                  type="button"
                  className="secondary"
                  onClick={() => setPanel("list")}
                >
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
              <p>
                You become the owner of the hospital and administrator of its
                first department.
              </p>
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
                <button
                  type="button"
                  className="secondary"
                  onClick={() => setPanel("list")}
                >
                  Back
                </button>
                <button
                  className="primary"
                  disabled={
                    busy || !hospitalName.trim() || !departmentName.trim()
                  }
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
              <p>
                This department is added to {hostHospital.name}. You administer
                it.
              </p>
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
                <button
                  type="button"
                  className="secondary"
                  onClick={() => setPanel("list")}
                >
                  Back
                </button>
                <button
                  className="primary"
                  disabled={busy || !departmentName.trim()}
                >
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
