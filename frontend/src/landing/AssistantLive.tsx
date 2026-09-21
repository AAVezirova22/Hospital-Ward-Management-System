"use client";

import { useEffect, useRef, useState } from "react";
import { useInView, useReducedMotion } from "motion/react";
import { ArrowRight } from "@phosphor-icons/react";
import { EcgLine } from "./EcgLine";

const BEATS = [
  {
    at: 0,
    kind: "user" as const,
    text: "Night census is in this spreadsheet.",
  },
  { at: 900, kind: "file" as const, text: "night-census.xlsx" },
  {
    at: 2200,
    kind: "assist" as const,
    text: "Four admissions from the sheet. Room 307 needs a second look.",
  },
  { at: 3800, kind: "proposal" as const, text: "" },
];

export function AssistantLive() {
  const reduce = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const inView = useInView(ref, { amount: 0.1 });
  const [step, setStep] = useState(BEATS.length);
  useEffect(() => {
    if (reduce) {
      setStep(BEATS.length);
      return;
    }
    let cancelled = false;
    let timers: number[] = [];
    const play = () => {
      setStep(0);
      timers = BEATS.map((beat, index) =>
        window.setTimeout(() => {
          if (!cancelled) setStep(index + 1);
        }, beat.at),
      );
    };
    play();
    const loop = window.setInterval(() => {
      timers.forEach((id) => window.clearTimeout(id));
      play();
    }, 12000);
    return () => {
      cancelled = true;
      timers.forEach((id) => window.clearTimeout(id));
      window.clearInterval(loop);
    };
  }, [reduce]);

  const visible = BEATS.slice(0, Math.max(step, reduce ? BEATS.length : 0));

  return (
    <section className="film-live" id="assistant" ref={ref}>
      <div className="film-live-copy">
        <p className="film-live-kicker">Operations assistant</p>
        <h2>Ask the ward. Confirm before anything writes.</h2>
        <p>
          The assistant uses the same authorized services as the rest of
          Medcore. It can read the floor, draft admissions from files, and never
          executes a write until a named person confirms an expiring proposal.
        </p>
        <ul className="film-live-points">
          <li>Local command mode, or an external model you configure.</li>
          <li>Uploads and connected folders, not a sweep of the whole PC.</li>
          <li>Confirm workflow runs in one transaction. Failures roll back.</li>
        </ul>
      </div>
      <div className="film-live-board" aria-label="Assistant demonstration">
        <header>
          <span>Operations assistant</span>
          <em>Cardiology</em>
        </header>
        <EcgLine className="film-ecg" draw={!reduce && inView} />
        <div className="film-live-thread">
          {visible.map((beat, index) =>
            beat.kind === "proposal" ? (
              <article className="film-proposal" key={index}>
                <p>Workflow · expires in 8 min</p>
                <ol>
                  <li>Create patient Kalina Ruseva</li>
                  <li>Admit to 307 · Dr. Veleva</li>
                  <li>Hold bed 2 until confirm</li>
                </ol>
                <span className="film-proposal-cta">
                  Confirm workflow
                  <ArrowRight size={14} weight="light" />
                </span>
              </article>
            ) : (
              <p className={`film-live-msg is-${beat.kind}`} key={index}>
                {beat.kind === "file" ? `File · ${beat.text}` : beat.text}
              </p>
            ),
          )}
        </div>
      </div>
    </section>
  );
}
