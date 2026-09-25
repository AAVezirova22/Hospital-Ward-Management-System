import { dateInTimeZone } from "../../date-time";

export type DueValue = {
  dueAt: string | null;
  dueOn?: string | null;
  dueTime?: string | null;
};

export function formatDue(value: DueValue, timeZone?: string) {
  if (value.dueAt)
    return new Date(value.dueAt).toLocaleString("en-GB", {
      dateStyle: "medium",
      timeStyle: "short",
      ...(timeZone ? { timeZone } : {}),
    });
  if (value.dueOn)
    return `${value.dueOn}${value.dueTime ? ` at ${value.dueTime}` : " (date only)"}`;
  return "No due date specified";
}

export function isOverdue(value: DueValue, timeZone?: string) {
  if (value.dueAt) return new Date(value.dueAt).getTime() < Date.now();
  if (value.dueOn) {
    const date = timeZone
      ? dateInTimeZone(new Date(), timeZone)
      : new Date().toLocaleDateString("sv-SE");
    return value.dueOn < date;
  }
  return false;
}

export function dueDay(value: DueValue, timeZone: string) {
  if (value.dueOn) return value.dueOn;
  if (!value.dueAt) return null;
  return dateInTimeZone(new Date(value.dueAt), timeZone);
}

export function dueSort(value: DueValue) {
  if (value.dueAt) return new Date(value.dueAt).getTime();
  if (value.dueOn) return new Date(`${value.dueOn}T23:59:59`).getTime();
  return Number.POSITIVE_INFINITY;
}
