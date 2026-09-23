"use client";
import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  BedDouble,
  ChevronDown,
} from "../icons";
import { api, activeDepartment, setActiveDepartment } from "../api";
import type { WorkspaceDepartment, WorkspaceHospital, WorkspaceList } from "../api/contracts";
import { ErrorBox, Modal } from "./workspace";
import { currentNames } from "./workspace-names";
import {
  JoinForm,
  CreateHospitalForm,
  CreateDepartmentForm,
  DepartmentTimeZoneForm,
} from "./workspace-forms";
import { WorkspaceList as HospitalList } from "./workspace-list";

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
    "list" | "join" | "hospital" | "department" | "timezone"
  >("list");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<Error | null>(null);
  const [joinCode, setJoinCode] = useState("");
  const [joinHint, setJoinHint] = useState("");
  const [hospitalName, setHospitalName] = useState("");
  const [departmentName, setDepartmentName] = useState("");
  const [timeZoneDepartment, setTimeZoneDepartment] = useState<WorkspaceDepartment>();
  const [timeZone, setTimeZone] = useState("");
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
  const saveTimeZone = async () => {
    if (!timeZoneDepartment) return;
    setBusy(true);
    setFormError(null);
    try {
      await api(`/workspaces/departments/${timeZoneDepartment.id}/timezone`, "PUT", { timeZone });
      await client.invalidateQueries();
      setPanel("list");
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
          <small>
            {current.department?.name || "Choose a department"}
            {current.department?.timeZone ? ` · ${current.department.timeZone}` : ""}
          </small>
        </div>
        <ChevronDown size={16} aria-hidden="true" />
      </button>
      {open && (
        <Modal title="Hospitals and departments" onClose={close}>
          <ErrorBox error={error || formError} />
          {isLoading && <p>Loading workspaces…</p>}
          {panel === "list" && (
            <HospitalList
              hospitals={data?.hospitals ?? []}
              currentDepartmentId={current.department?.id}
              busy={busy}
              onOpenDepartment={(id) => void openDepartment(id)}
              onReveal={(hospital, id) => void revealAndCopy(hospital, id)}
              onRotate={(hospital, id, options) =>
                void rotate(hospital, id, options)
              }
              onLeaveDepartment={(id) =>
                void (async () => {
                  setBusy(true);
                  setFormError(null);
                  try {
                    await api(`/workspaces/departments/${id}/leave`, "POST", {});
                    if (Number(activeDepartment()) === id)
                      setActiveDepartment(null);
                    await client.invalidateQueries();
                  } catch (e) {
                    setFormError(e as Error);
                  } finally {
                    setBusy(false);
                  }
                })()
              }
              onLeaveHospital={(id) =>
                void (async () => {
                  setBusy(true);
                  setFormError(null);
                  try {
                    await api(`/workspaces/hospitals/${id}/leave`, "POST", {});
                    setActiveDepartment(null);
                    await client.invalidateQueries();
                  } catch (e) {
                    setFormError(e as Error);
                  } finally {
                    setBusy(false);
                  }
                })()
              }
              onNewDepartment={(hospital) => {
                setHostHospital(hospital);
                setDepartmentName("");
                setPanel("department");
              }}
              onTimeZone={(department) => {
                setTimeZoneDepartment(department);
                setTimeZone(department.timeZone);
                setFormError(null);
                setPanel("timezone");
              }}
              onJoin={() => {
                setJoinCode("");
                setPanel("join");
              }}
              onCreateHospital={() => {
                setHospitalName("");
                setDepartmentName("");
                setPanel("hospital");
              }}
            />
          )}
          {panel === "join" && (
            <JoinForm
              joinCode={joinCode}
              joinHint={joinHint}
              busy={busy}
              onCode={setJoinCode}
              onBack={() => setPanel("list")}
              onJoin={() =>
                void submit(() =>
                  api("/workspaces/join", "POST", { code: joinCode }),
                )
              }
            />
          )}
          {panel === "hospital" && (
            <CreateHospitalForm
              hospitalName={hospitalName}
              departmentName={departmentName}
              busy={busy}
              onHospitalName={setHospitalName}
              onDepartmentName={setDepartmentName}
              onBack={() => setPanel("list")}
              onCreate={() =>
                void submit(() =>
                  api("/workspaces/hospitals", "POST", {
                    name: hospitalName,
                    departmentName,
                  }),
                )
              }
            />
          )}
          {panel === "department" && hostHospital && (
            <CreateDepartmentForm
              hostHospital={hostHospital}
              departmentName={departmentName}
              busy={busy}
              onDepartmentName={setDepartmentName}
              onBack={() => setPanel("list")}
              onCreate={() =>
                void submit(() =>
                  api(
                    `/workspaces/hospitals/${hostHospital.id}/departments`,
                    "POST",
                    { name: departmentName },
                  ),
                )
              }
            />
          )}
          {panel === "timezone" && timeZoneDepartment && (
            <DepartmentTimeZoneForm
              departmentName={timeZoneDepartment.name}
              timeZone={timeZone}
              busy={busy}
              onTimeZone={setTimeZone}
              onBack={() => setPanel("list")}
              onSave={() => void saveTimeZone()}
            />
          )}
        </Modal>
      )}
    </>
  );
}
