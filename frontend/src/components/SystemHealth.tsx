"use client";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
type Health = {
  status: string;
  components?: Record<string, { status: string }>;
};
export function SystemHealth() {
  const health = useQuery({
    queryKey: ["/management/health"],
    queryFn: () => api<Health>("/management/health"),
    refetchInterval: 30000,
  });
  const metric = useQuery({
    queryKey: ["/management/metrics/http.server.requests"],
    queryFn: () =>
      api<{ measurements: { statistic: string; value: number }[] }>(
        "/management/metrics/http.server.requests",
      ),
    refetchInterval: 30000,
  });
  const count = metric.data?.measurements.find(
    (m) => m.statistic === "COUNT",
  )?.value;
  return (
    <details className="panel system-health">
      <summary>
        System health ·{" "}
        {health.isLoading
          ? "Checking"
          : health.error
            ? "Unavailable"
            : health.data?.status}
      </summary>
      {health.error && (
        <p role="alert">
          Health service unavailable. Check the API connection.
        </p>
      )}
      <dl>
        <dt>API</dt>
        <dd>{health.data?.status ?? "Unknown"}</dd>
        <dt>Database</dt>
        <dd>{health.data?.components?.db?.status ?? "Unknown"}</dd>
        <dt>HTTP requests since startup</dt>
        <dd>{count ?? (metric.isLoading ? "Loading" : "Unavailable")}</dd>
      </dl>
      <small>Administrator visibility · refreshes every 30 seconds</small>
    </details>
  );
}
