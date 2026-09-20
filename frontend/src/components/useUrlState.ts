"use client";
import { useEffect, useState } from "react";
export function useUrlState(key: string, fallback = "") {
  const [value, setValue] = useState(fallback);
  useEffect(() => {
    const read = () =>
      setValue(
        new URLSearchParams(window.location.search).get(key) ?? fallback,
      );
    read();
    window.addEventListener("popstate", read);
    window.addEventListener("medcore-url", read);
    return () => {
      window.removeEventListener("popstate", read);
      window.removeEventListener("medcore-url", read);
    };
  }, [key, fallback]);
  function update(next: string) {
    setValue(next);
    const url = new URL(window.location.href);
    if (next) url.searchParams.set(key, next);
    else url.searchParams.delete(key);
    window.history.replaceState(null, "", url);
    window.dispatchEvent(new Event("medcore-url"));
  }
  return [value, update] as const;
}
