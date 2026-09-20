"use client";

import { useEffect, useState } from "react";
import { useReducedMotion } from "motion/react";

const LINES = [
  "14 on the floor · Cardiology",
  "3 free beds in 307",
  "Assistant standing by",
  "Workflow waiting for confirm",
];

export function Pulse() {
  const reduce = useReducedMotion();
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (reduce) return;
    const timer = window.setInterval(
      () => setIndex((current) => (current + 1) % LINES.length),
      2800,
    );
    return () => window.clearInterval(timer);
  }, [reduce]);
  return (
    <p className="film-pulse" aria-live="polite">
      <i className="film-pulse-dot" />
      <span>Live</span>
      {LINES[index]}
    </p>
  );
}
