"use client";

import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import { useInView, useReducedMotion } from "motion/react";

const Clouds = dynamic(() => import("../vendor/canvasui/Clouds"), {
  ssr: false,
});

export function Mist() {
  const reduce = useReducedMotion();
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref);
  const [wide, setWide] = useState(false);
  useEffect(() => {
    const screen = window.matchMedia("(min-width: 801px)");
    const update = () => setWide(screen.matches && !document.hidden);
    update();
    screen.addEventListener("change", update);
    document.addEventListener("visibilitychange", update);
    return () => {
      screen.removeEventListener("change", update);
      document.removeEventListener("visibilitychange", update);
    };
  }, []);
  return (
    <div
      ref={ref}
      className="film-mist"
      aria-hidden="true"
      data-canvas-ui="clouds"
    >
      {!reduce && wide && inView ? (
        <Clouds
          className="film-clouds"
          color={[0.58, 0.66, 0.74]}
          speed={0.12}
          scale={1.2}
          cover={0.05}
          density={1.4}
          opacity={0.2}
          shadow={0}
          quality={0.32}
        >
          <div className="film-mist-field" />
        </Clouds>
      ) : null}
    </div>
  );
}
