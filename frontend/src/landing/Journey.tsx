"use client";

import { useRef } from "react";
import {
  motion,
  useReducedMotion,
  useScroll,
  useTransform,
  type MotionValue,
} from "motion/react";
import { EcgLine } from "./EcgLine";

const STEPS = [
  {
    title: "Book",
    copy: "A time that fits, a specialist who already has your notes.",
  },
  {
    title: "Diagnose",
    copy: "Imaging and conversation in the same week, not a month of waiting.",
  },
  {
    title: "Treat",
    copy: "The plan is written with you in the room, then carried out by the same team.",
  },
  {
    title: "Recover",
    copy: "Follow-up that does not vanish after discharge.",
  },
];

export function Journey() {
  const reduce = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start start", "end end"],
  });
  return (
    <section className="film-journey" id="patients" ref={ref}>
      <div className="film-journey-pin">
        <EcgLine className="film-ecg" draw={!reduce} />
        <div className="film-journey-steps">
          {STEPS.map((item, index) => (
            <Step
              key={item.title}
              index={index}
              item={item}
              progress={scrollYProgress}
              reduce={Boolean(reduce)}
            />
          ))}
        </div>
      </div>
    </section>
  );
}

function Step({
  index,
  item,
  progress,
  reduce,
}: {
  index: number;
  item: (typeof STEPS)[number];
  progress: MotionValue<number>;
  reduce: boolean;
}) {
  const opacity = useTransform(progress, (value) => {
    if (reduce) return 1;
    const current = value * (STEPS.length - 1);
    return Math.abs(current - index) < 0.62 ? 1 : 0.22;
  });
  return (
    <motion.article className="film-step is-now" style={{ opacity }}>
      <b>{item.title}</b>
      <p>{item.copy}</p>
    </motion.article>
  );
}
