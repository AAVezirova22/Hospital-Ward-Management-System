export function normalizeCapabilities(value: unknown): string[] {
  const values =
    typeof value === "string"
      ? value.split(",")
      : Array.isArray(value)
        ? value
        : [];
  return [...new Set(
    values
      .filter((item): item is string => typeof item === "string")
      .map((item) => item.trim().toLowerCase())
      .filter(Boolean),
  )].sort();
}

export function missingCapabilities(
  required: unknown,
  available: unknown,
): string[] {
  const have = new Set(normalizeCapabilities(available));
  return normalizeCapabilities(required).filter((capability) => !have.has(capability));
}
