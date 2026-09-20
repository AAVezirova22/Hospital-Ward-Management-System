"use client";

import { useEffect, useState } from "react";

const BEATS = [
  { id: "product", label: "Product" },
  { id: "assistant", label: "Assistant" },
  { id: "workflow", label: "Workflow" },
  { id: "specialists", label: "Floor" },
  { id: "start", label: "Start" },
] as const;

export function ProgressRail() {
  const [here, setHere] = useState("product");
  useEffect(() => {
    const nodes = BEATS.map((beat) => document.getElementById(beat.id)).filter(
      (node): node is HTMLElement => Boolean(node),
    );
    if (!nodes.length) return;
    const seen = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (visible?.target.id) setHere(visible.target.id);
      },
      { threshold: [0.25, 0.45, 0.6], rootMargin: "-18% 0px -35% 0px" },
    );
    nodes.forEach((node) => seen.observe(node));
    return () => seen.disconnect();
  }, []);
  return (
    <nav className="film-rail" aria-label="Page scenes">
      {BEATS.map((beat) => (
        <a
          key={beat.id}
          href={`#${beat.id}`}
          className={here === beat.id ? "is-here" : undefined}
        >
          <i />
          {beat.label}
        </a>
      ))}
    </nav>
  );
}
