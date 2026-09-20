"use client";
import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { activeDepartment } from "../api";

export function LiveOperations() {
  const client = useQueryClient();
  const [live, setLive] = useState(false);
  useEffect(() => {
    let stream: EventSource | undefined;
    let debounce: ReturnType<typeof setTimeout> | undefined;
    const refresh = () => {
      if (debounce) return;
      debounce = setTimeout(() => {
        debounce = undefined;
        void client.invalidateQueries({
          predicate: (q) =>
            typeof q.queryKey[0] === "string" &&
            /^\/(rooms|admissions|patients|reports|audit)/.test(q.queryKey[0]),
        });
      }, 250);
    };
    const connect = () => {
      stream?.close();
      setLive(false);
      if (document.hidden) return;
      const department = activeDepartment();
      const url = department
        ? `/api/v1/operations/stream?departmentId=${encodeURIComponent(department)}`
        : "/api/v1/operations/stream";
      stream = new EventSource(url);
      stream.addEventListener("ready", () => {
        setLive(true);
        refresh();
      });
      stream.addEventListener("changed", refresh);
      stream.onerror = () => setLive(false);
    };
    connect();
    document.addEventListener("visibilitychange", connect);
    window.addEventListener("workspace-changed", connect);
    return () => {
      stream?.close();
      clearTimeout(debounce);
      document.removeEventListener("visibilitychange", connect);
      window.removeEventListener("workspace-changed", connect);
    };
  }, [client]);
  return (
    <span
      className={`connection-state ${live ? "connected" : ""}`}
      role="status"
    >
      {live ? "Live updates connected" : "Periodic refresh active"}
    </span>
  );
}
