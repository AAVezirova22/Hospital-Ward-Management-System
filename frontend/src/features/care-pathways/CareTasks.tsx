"use client";

import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { api, activeDepartment, patientHref } from "../../api";
import type { WorkspaceList } from "../../api/contracts";
import { ErrorBox, Link, Title, useUser } from "../../components/workspace";
import type { CareTask } from "./types";
import { ReminderSettings } from "./ReminderSettings";
import { dueSort, formatDue, isOverdue } from "./due";

type ReminderOpen = { taskId: number };

export function CareTasks() {
  const user = useUser();
  const params = useSearchParams();
  const reminder = params.get("reminder");
  const department = activeDepartment();
  const client = useQueryClient();
  const workspaces = useQuery({
    queryKey: ["/workspaces", department],
    queryFn: () => api<WorkspaceList>("/workspaces"),
  });
  const timeZone = workspaces.data?.timeZone ?? "UTC";
  const tasks = useQuery({
    queryKey: ["care-tasks", department],
    queryFn: () => api<CareTask[]>("/care-tasks"),
  });
  const assignees = useQuery({
    queryKey: ["care-workflow-assignees", department],
    queryFn: () =>
      api<{ id: number; displayName: string; role: string }[]>(
        "/care-workflows/assignees",
      ),
    enabled: user.role !== "DOCTOR",
  });
  const [focusedId, setFocusedId] = useState<number | null>(null);
  const [openError, setOpenError] = useState<Error | null>(null);
  const [snoozeBusy, setSnoozeBusy] = useState(false);
  const [snoozeNotice, setSnoozeNotice] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<Error | null>(null);
  useEffect(() => {
    if (!reminder) return;
    let active = true;
    api<ReminderOpen>(`/task-reminders/open/${encodeURIComponent(reminder)}`)
      .then((result) => {
        if (active) setFocusedId(result.taskId);
      })
      .catch((cause) => {
        if (active) setOpenError(cause as Error);
      });
    return () => {
      active = false;
    };
  }, [reminder]);
  async function change(task: CareTask, status: CareTask["status"]) {
    setBusyId(task.id);
    setError(null);
    try {
      await api(`/care-tasks/${task.id}`, "PATCH", {
        status,
        assignedUserId: task.assignedUserId,
        version: task.version,
      });
      await client.invalidateQueries({ queryKey: ["care-tasks"] });
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusyId(null);
    }
  }
  async function assign(task: CareTask, assignedUserId: number) {
    setBusyId(task.id);
    setError(null);
    try {
      await api(`/care-tasks/${task.id}`, "PATCH", {
        status: task.status,
        assignedUserId,
        version: task.version,
      });
      await client.invalidateQueries({ queryKey: ["care-tasks"] });
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusyId(null);
    }
  }
  const sorted = [...(tasks.data ?? [])].sort(
    (a, b) => dueSort(a) - dueSort(b),
  );
  return (
    <div className="care-tasks-page">
      <Title
        eyebrow="Care tasks"
        title="Follow-up work."
        description="See assigned tasks, due times, dependencies and completion status."
      />
      <ReminderSettings />
      {reminder && focusedId && (
        <div className="care-reminder-action panel">
          <p>
            You opened a private task reminder. Sign-in is required to see its
            details.
          </p>
          <button
            className="secondary"
            disabled={snoozeBusy}
            onClick={async () => {
              setSnoozeBusy(true);
              setOpenError(null);
              try {
                await api(
                  `/task-reminders/snooze/${encodeURIComponent(reminder)}`,
                  "POST",
                );
                setSnoozeNotice(
                  "Reminder snoozed. The task remains available here.",
                );
              } catch (cause) {
                setOpenError(cause as Error);
              } finally {
                setSnoozeBusy(false);
              }
            }}
          >
            Snooze reminder
          </button>
          {snoozeNotice && <p role="status">{snoozeNotice}</p>}
        </div>
      )}
      <ErrorBox error={tasks.error || workspaces.error || openError || error} />
      {tasks.isLoading && <p>Loading care tasks…</p>}
      {!tasks.isLoading && !sorted.length && (
        <div className="panel care-empty">
          <h2>No care tasks are assigned here.</h2>
          <p>Approved pathways create trackable tasks for the care team.</p>
        </div>
      )}
      <div className="care-task-list">
        {sorted.map((task) => (
          <article
            key={task.id}
            className={`panel care-task-row${focusedId === task.id ? " focused" : ""}`}
            id={`task-${task.id}`}
          >
            <div>
              <div className="care-task-head">
                <span className="status">
                  {task.status.replaceAll("_", " ")}
                </span>
                {isOverdue(task, timeZone) &&
                  task.status !== "COMPLETED" &&
                  task.status !== "CANCELLED" && (
                    <span className="care-overdue">Overdue</span>
                  )}
              </div>
              <h2>{task.title}</h2>
              <p>{task.description}</p>
              <small>
                Due {formatDue(task, timeZone)} · {task.ownerRole.replaceAll("_", " ")} ·{" "}
                {task.dependencyState}
              </small>
              {task.taskOrigin === "DOCUMENT" && task.sourceExcerpt && (
                <blockquote className="care-task-source">
                  “{task.sourceExcerpt}”<small>{task.sourceLocation}</small>
                </blockquote>
              )}
              <div>
                <Link
                  className="text-button"
                  to={patientHref({ id: task.patientId })}
                >
                  Open patient dossier
                </Link>
              </div>
            </div>
            <div className="care-task-actions">
              {user.role !== "DOCTOR" &&
                task.status !== "COMPLETED" &&
                task.status !== "CANCELLED" && (
                  <label>
                    Assigned to
                    <select
                      aria-label={`Assign ${task.title}`}
                      value={task.assignedUserId ?? ""}
                      disabled={busyId === task.id}
                      onChange={(event) => {
                        if (event.target.value)
                          void assign(task, Number(event.target.value));
                      }}
                    >
                      <option value="">Unassigned</option>
                      {(assignees.data ?? [])
                        .filter((person) => person.role === task.ownerRole)
                        .map((person) => (
                          <option key={person.id} value={person.id}>
                            {person.displayName}
                          </option>
                        ))}
                    </select>
                  </label>
                )}
              {task.status === "OPEN" && (
                <button
                  className="secondary"
                  disabled={
                    busyId === task.id || task.dependencyState !== "READY"
                  }
                  onClick={() => void change(task, "IN_PROGRESS")}
                >
                  Start
                </button>
              )}
              {(task.status === "OPEN" || task.status === "IN_PROGRESS") && (
                <button
                  className="primary"
                  disabled={
                    busyId === task.id || task.dependencyState !== "READY"
                  }
                  onClick={() => void change(task, "COMPLETED")}
                >
                  Mark complete
                </button>
              )}
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
