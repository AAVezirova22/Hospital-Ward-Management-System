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
    title: "Arrive",
    copy: "A file, an iMessage, or a typed command lands in the assistant.",
  },
  {
    title: "Plan",
    copy: "Named steps, source fields, and the rooms that actually have beds.",
  },
  {
    title: "Confirm",
    copy: "An owned, expiring proposal. A person still has to say yes.",
  },
  {
    title: "Write",
    copy: "The same authorized services as the rest of Medcore. Failures roll back.",
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
