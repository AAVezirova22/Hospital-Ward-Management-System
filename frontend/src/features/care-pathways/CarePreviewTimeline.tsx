import { dateInTimeZone } from "../../date-time";
import { dueDay, formatDue, isOverdue } from "./due";
import type { CarePreview, CareTaskDefinition } from "./types";

type PreviewTask = CarePreview["tasks"][number];

export function previewDayGroups(preview: CarePreview, timeZone: string) {
  const groups = new Map<string | null, PreviewTask[]>();
  const sorted = [...preview.tasks].sort((left, right) => {
    const leftDay = dueDay(left, timeZone);
    const rightDay = dueDay(right, timeZone);
    if (leftDay === rightDay) return 0;
    if (leftDay === null) return 1;
    if (rightDay === null) return -1;
    return leftDay.localeCompare(rightDay);
  });
  for (const task of sorted) {
    const day = dueDay(task, timeZone);
    groups.set(day, [...(groups.get(day) ?? []), task]);
  }
  return [...groups].map(([day, tasks]) => ({ day, tasks }));
}

function dayHeading(day: string | null, baseTime: string, timeZone: string) {
  if (!day) return "No due day set";
  const baseDay = dateInTimeZone(new Date(baseTime), timeZone);
  const daysFromLaunch =
    (Date.parse(`${day}T00:00:00Z`) -
      Date.parse(`${baseDay}T00:00:00Z`)) /
    86_400_000;
  const label = daysFromLaunch < 0 ? "Before launch" : `Day ${daysFromLaunch + 1}`;
  const calendarDay = new Intl.DateTimeFormat("en-GB", {
    dateStyle: "full",
    timeZone: "UTC",
  }).format(new Date(`${day}T12:00:00Z`));
  return `${label} · ${calendarDay}`;
}

export function CarePreviewTimeline({
  preview,
  timeZone,
  assignees = [],
}: {
  preview: CarePreview;
  timeZone: string;
  assignees?: {
    id: number;
    displayName: string;
    role: CareTaskDefinition["ownerRole"];
  }[];
}) {
  const titles = new Map(preview.tasks.map((task) => [task.key, task.title]));
  const people = new Map(assignees.map((person) => [person.id, person.displayName]));
  return (
    <div className="care-preview-timeline" aria-label="Day-by-day pathway preview">
      <p className="care-preview-zone">Scheduled days use {timeZone}.</p>
      {previewDayGroups(preview, timeZone).map(({ day, tasks }) => (
        <section className="care-timeline-day" key={day ?? "unscheduled"}>
          <h4>{dayHeading(day, preview.baseTime, timeZone)}</h4>
          <ol className="care-preview-list">
            {tasks.map((task) => (
              <li key={task.key}>
                <div>
                  <strong>{task.title}</strong>
                  {task.description && <p>{task.description}</p>}
                  <small>
                    {task.ownerRole.replaceAll("_", " ")} ·{" "}
                    {task.assignedUserId
                      ? people.get(task.assignedUserId) ?? "Assigned clinician"
                      : "Unassigned"}{" "}
                    · Due {formatDue(task, timeZone)}
                  </small>
                  <small>
                    {task.dependsOn.length
                      ? `After ${task.dependsOn.map((key) => titles.get(key) ?? key).join(", ")}`
                      : "No dependency"}{" "}
                    · {task.dependencyState}
                  </small>
                  {isOverdue(task, timeZone) && (
                    <span className="care-overdue">Overdue</span>
                  )}
                  {task.taskOrigin === "DOCUMENT" && task.sourceExcerpt && (
                    <blockquote className="care-task-source">
                      “{task.sourceExcerpt}”
                      <small>
                        {[task.sourceName, task.sourceLocation]
                          .filter(Boolean)
                          .join(" · ")}
                      </small>
                    </blockquote>
                  )}
                </div>
              </li>
            ))}
          </ol>
        </section>
      ))}
    </div>
  );
}
