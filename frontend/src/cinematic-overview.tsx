"use client";
import { type ReactNode } from "react";
import { Reveal, Atmosphere } from "./cinematic";

export function OverviewHero({ children }: { children: ReactNode }) {
  return (
    <Reveal className="overview-hero">
      <div className="overview-hero-image" aria-hidden="true" />
      <Atmosphere />
      <div className="cinema-grain" aria-hidden="true" />
      <div className="overview-hero-copy">{children}</div>
      <div className="overview-hero-caption">
        Made for the people
        <br />
        who care for people.
      </div>
    </Reveal>
  );
}
