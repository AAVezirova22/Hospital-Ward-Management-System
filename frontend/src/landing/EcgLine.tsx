"use client";

export function EcgLine({
  className,
  draw,
}: {
  className?: string;
  draw?: boolean;
}) {
  return (
    <svg
      className={className}
      viewBox="0 0 240 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        className={draw ? "film-ecg-draw" : undefined}
        d="M0 12 H52 L58 12 L64 4 L72 20 L78 8 L84 12 H240"
        stroke="currentColor"
        strokeWidth="1.15"
        strokeLinejoin="miter"
        pathLength="1"
      />
    </svg>
  );
}
