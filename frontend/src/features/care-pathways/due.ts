export type DueValue = {
  dueAt: string | null;
  dueOn?: string | null;
  dueTime?: string | null;
};

export function formatDue(value: DueValue) {
  if (value.dueAt) return new Date(value.dueAt).toLocaleString();
  if (value.dueOn)
    return `${value.dueOn}${value.dueTime ? ` at ${value.dueTime}` : " (date only)"}`;
  return "No due date specified";
}

export function isOverdue(value: DueValue) {
  if (value.dueAt) return new Date(value.dueAt).getTime() < Date.now();
  if (value.dueOn) {
    const today = new Date().toLocaleDateString("sv-SE");
    return value.dueOn < today;
  }
  return false;
}

export function dueSort(value: DueValue) {
  if (value.dueAt) return new Date(value.dueAt).getTime();
  if (value.dueOn) return new Date(`${value.dueOn}T23:59:59`).getTime();
  return Number.POSITIVE_INFINITY;
}
