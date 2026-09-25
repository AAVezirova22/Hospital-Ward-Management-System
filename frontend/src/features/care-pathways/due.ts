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

export function dueSort(value: DueValue, timeZone?: string) {
  if (timeZone) {
    const day = dueDay(value, timeZone);
    if (!day) return Number.POSITIVE_INFINITY;
    const dayStart = Date.parse(`${day}T00:00:00Z`);
    if (value.dueAt) {
      const parts = new Intl.DateTimeFormat("en-GB", {
        timeZone,
        hourCycle: "h23",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      }).formatToParts(new Date(value.dueAt));
      const clock = Object.fromEntries(
        parts.map(({ type, value: part }) => [type, part]),
      );
      return (
        dayStart +
        Number(clock.hour) * 3_600_000 +
        Number(clock.minute) * 60_000 +
        Number(clock.second) * 1_000
      );
    }
    if (value.dueTime) {
      const [hour, minute, second = "0"] = value.dueTime.split(":");
      return (
        dayStart +
        Number(hour) * 3_600_000 +
        Number(minute) * 60_000 +
        Number(second) * 1_000
      );
    }
    return dayStart + 86_399_999;
  }
  if (value.dueAt) return new Date(value.dueAt).getTime();
  if (value.dueOn) return new Date(`${value.dueOn}T23:59:59`).getTime();
  return Number.POSITIVE_INFINITY;
}
