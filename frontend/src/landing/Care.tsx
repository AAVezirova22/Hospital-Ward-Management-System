"use client";

import { motion } from "motion/react";
import { FilmMedia } from "./FilmMedia";
import { clip, still } from "./media";

const STATS = [
  { value: "186", label: "consultants across the campus" },
  { value: "14", label: "specialist departments" },
  { value: "42,800", label: "patients treated last year" },
];

export function Care() {
  return (
    <section className="film-care" id="numbers">
      <FilmMedia
        still={still("care")}
        video={clip("care")}
        alt="A nurse passing behind hospital glass at sunrise"
      />
      <div className="film-care-shade" />
      <div className="film-care-copy">
        <h2>Care when it matters most</h2>
        <div className="film-stats">
          {STATS.map((stat, index) => (
            <motion.p
              className="film-stat"
              key={stat.label}
              initial={false}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{
                duration: 0.8,
                delay: index * 0.12,
                ease: [0.32, 0.72, 0, 1],
              }}
            >
              <b>{stat.value}</b>
              <span>{stat.label}</span>
            </motion.p>
          ))}
        </div>
      </div>
    </section>
  );
}
