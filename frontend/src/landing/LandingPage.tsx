"use client";

import { useEffect, useRef } from "react";
import { useScroll, useMotionValueEvent } from "motion/react";
import { Booking } from "./Booking";
import { Care } from "./Care";
import { Departments } from "./Departments";
import { FilmFooter } from "./FilmFooter";
import { FilmNav } from "./FilmNav";
import { Hero } from "./Hero";
import { Journey } from "./Journey";
import { ProgressRail } from "./ProgressRail";
import { Story } from "./Story";
import { Team } from "./Team";
import { Technology } from "./Technology";
import { Visit } from "./Visit";

export function LandingPage() {
  const root = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: root,
    offset: ["start start", "end end"],
  });
  useMotionValueEvent(scrollYProgress, "change", (value) => {
    const day =
      value < 0.48
        ? value * 0.35
        : value < 0.62
          ? 0.08
          : 0.08 + (value - 0.62) * 2.2;
    root.current?.style.setProperty(
      "--day",
      String(Math.min(1, Math.max(0, day))),
    );
  });
  useEffect(() => {
    document.documentElement.dataset.film = "ready";
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && location.hash === "#booking") {
        location.hash = "care";
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  return (
    <div className="film" ref={root}>
      <a className="film-skip" href="#care">
        Skip to content
      </a>
      <div className="film-void" aria-hidden="true" />
      <div className="film-grain" aria-hidden="true" />
      <FilmNav />
      <ProgressRail />
      <main className="film-stage">
        <Hero />
        <Care />
        <Departments />
        <Team />
        <Technology />
        <Journey />
        <Story />
        <Visit />
      </main>
      <FilmFooter />
      <Booking />
    </div>
  );
}
