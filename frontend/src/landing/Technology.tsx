"use client";

import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import { useInView, useReducedMotion } from "motion/react";
import { FilmMedia } from "./FilmMedia";
import { still } from "./media";

const Glass = dynamic(() => import("../vendor/canvasui/Glass"), { ssr: false });

function Gallery() {
  return (
    <div className="film-tech-grid">
      <FilmMedia still={still("mri")} alt="MRI suite at night" />
      <FilmMedia still={still("or")} alt="Operating theatre between cases" />
      <FilmMedia
        still={still("robotic")}
        alt="Surgical robotic console in a dim procedure room"
      />
    </div>
  );
}

export function Technology() {
  const reduce = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const inView = useInView(ref, { amount: 0.2 });
  const [wide, setWide] = useState(false);
  useEffect(() => {
    const screen = window.matchMedia("(min-width: 801px)");
    const update = () => setWide(screen.matches);
    update();
    screen.addEventListener("change", update);
    return () => screen.removeEventListener("change", update);
  }, []);
  const lens = !reduce && wide && inView;
  return (
    <section className="film-tech" id="technology" ref={ref}>
      <div className="film-tech-copy">
        <h2>Planner, theatre, the rooms you already have</h2>
        <p>
          Ward maps, bed locks, and the imaging floor photographed as they are.
          The assistant never bypasses capacity checks.
        </p>
      </div>
      {lens ? (
        <Glass
          className="film-tech-inner"
          size={132}
          ior={1.38}
          aberration={0.35}
          shine={0.45}
          follow={0.18}
          zoom={1.35}
          targets="[data-glass-target]"
        >
          <div data-glass-target>
            <Gallery />
          </div>
        </Glass>
      ) : (
        <Gallery />
      )}
    </section>
  );
}
