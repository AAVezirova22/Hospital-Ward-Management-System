/** Default only: never rewrite a time entered by the user or relax server validation. */
export function defaultProcedureTime(
  admissionStart?: string,
  now = new Date(),
) {
  const start = admissionStart ? Date.parse(admissionStart) : NaN;
  // Java Instants retain nanoseconds; JS Dates truncate to milliseconds.
  // Advance one millisecond so the default cannot precede that exact Instant.
  const earliest = Number.isFinite(start) ? start + 1 : 0;
  const value = new Date(Math.max(now.getTime(), earliest));
  return new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 23);
}
