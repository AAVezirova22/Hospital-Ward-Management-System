"use client";

import { useEffect, useRef, useState } from "react";
import { useReducedMotion } from "motion/react";

const THREAD = [
  { at: 0, who: "staff", text: "Census from the night shift." },
  { at: 800, who: "staff", text: "night-census.xlsx" },
  {
    at: 2100,
    who: "medcore",
    text: "Four rows look like admissions. 307 is one bed short unless we discharge Petrov first.",
  },
  {
    at: 4000,
    who: "staff",
    text: "Draft the workflow. I will confirm in the ward.",
  },
];

export function Messages() {
  const reduce = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const [count, setCount] = useState(THREAD.length);
  useEffect(() => {
    if (reduce) {
      setCount(THREAD.length);
      return;
    }
    let cancelled = false;
    let timers: number[] = [];
    const play = () => {
      setCount(0);
      timers = THREAD.map((beat, index) =>
        window.setTimeout(() => {
          if (!cancelled) setCount(index + 1);
        }, beat.at),
      );
    };
    play();
    const loop = window.setInterval(() => {
      timers.forEach((id) => window.clearTimeout(id));
      play();
    }, 9000);
    return () => {
      cancelled = true;
      timers.forEach((id) => window.clearTimeout(id));
      window.clearInterval(loop);
    };
  }, [reduce]);

  return (
    <section className="film-msg" id="workflow" ref={ref}>
      <div className="film-msg-copy">
        <h2>From files and requests, into a confirmed workflow.</h2>
        <p>
          Upload a spreadsheet, connect a folder, or type a request in the
          Medcore workspace. The assistant drafts admissions, transfers, and
          procedures for review. A person confirms each workflow in Medcore
          before records change.
        </p>
        <ol className="film-msg-steps">
          <li>Source arrives: an upload, a connected folder, or a typed request.</li>
          <li>Assistant proposes named steps with source fields.</li>
          <li>
            You confirm. The same business services as the rest of the app run
            it.
          </li>
        </ol>
      </div>
      <div className="film-phone-shell" aria-label="Messages demonstration">
        <div className="film-phone-notch" />
        <p className="film-phone-label">Medcore · Cardiology</p>
        <div className="film-phone-thread">
          {THREAD.slice(0, count).map((beat, index) => (
            <p
              className={`film-bubble is-${beat.who}`}
              key={`${beat.at}-${index}`}
            >
              {beat.text}
            </p>
          ))}
        </div>
      </div>
    </section>
  );
}
