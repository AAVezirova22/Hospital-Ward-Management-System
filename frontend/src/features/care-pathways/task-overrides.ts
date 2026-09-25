import type { CareTaskDefinition } from "./types";

export type CareTaskOverride = Partial<CareTaskDefinition> & {
  key: string;
};

export function buildTaskOverrides(
  tasks: CareTaskDefinition[],
  originals: CareTaskDefinition[],
): CareTaskOverride[] {
  const byKey = new Map(originals.map((task) => [task.key, task]));
  return tasks.flatMap((task) => {
    const original = byKey.get(task.key);
    if (!original) return [];
    const changes: CareTaskOverride = { key: task.key };
    if (task.title !== original.title) changes.title = task.title;
    if (task.description !== original.description)
      changes.description = task.description;
    if (task.ownerRole !== original.ownerRole) changes.ownerRole = task.ownerRole;
    if (task.assignedUserId !== original.assignedUserId)
      changes.assignedUserId = task.assignedUserId;
    if (task.dueOffsetMinutes !== original.dueOffsetMinutes)
      changes.dueOffsetMinutes = task.dueOffsetMinutes;
    if (JSON.stringify(task.dependsOn) !== JSON.stringify(original.dependsOn))
      changes.dependsOn = task.dependsOn;
    return Object.keys(changes).length > 1 ? [changes] : [];
  });
}

export function validPatientTasks(
  tasks: CareTaskDefinition[],
  originals: CareTaskDefinition[],
) {
  return (
    tasks.length > 0 &&
    tasks.length === originals.length &&
    tasks.every(
      (task) =>
        !!task.title.trim() &&
        Number.isInteger(task.dueOffsetMinutes) &&
        task.dueOffsetMinutes >= 0 &&
        task.dueOffsetMinutes <= 525600,
    )
  );
}
