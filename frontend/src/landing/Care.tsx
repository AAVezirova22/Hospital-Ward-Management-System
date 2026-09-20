"use client";

import dynamic from "next/dynamic";
import { motion } from "motion/react";
import { FilmMedia } from "./FilmMedia";
import { still } from "./media";
import { useGpuScene } from "./gpu";

const Displacement = dynamic(() => import("../vendor/canvasui/Displacement"), {
  ssr: false,
});

const STATS = [
  { value: "186", label: "consultants across the campus" },
  { value: "14", label: "specialist departments" },
  { value: "42,800", label: "patients treated last year" },
];

export function Care() {
  const gpu = useGpuScene();
  const photo = (
    <FilmMedia
      still={still("care")}
      alt="A nurse passing behind hospital glass at sunrise"
    />
  );
  return (
    <section className="film-care" id="numbers">
      {gpu ? (
        <Displacement
          className="film-care-gpu"
          style={{ position: "absolute", inset: 0 }}
          grid={36}
          radius={0.16}
          strength={0.08}
          threshold={140}
          scramble={0}
          aberration={0.35}
          grain={0.04}
          shift={0.7}
        >
          {photo}
        </Displacement>
      ) : (
        photo
      )}
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
