"use client";

import { useRef } from "react";
import dynamic from "next/dynamic";
import { useInView } from "motion/react";
import { useGpuScene } from "./gpu";
import { useSiteMotion } from "./SiteMotion";

const Clouds = dynamic(() => import("../vendor/canvasui/Clouds"), {
  ssr: false,
});

/** Canvas UI's WebGL overlay: artwork only, no experimental DOM capture required. */
export function AmbientLight() {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref);
  const gpu = useGpuScene();
  const { ready, reduced } = useSiteMotion();
  return (
    <div ref={ref} className="mc-ambient" aria-hidden="true">
      {ready && gpu && inView && !reduced ? (
        <Clouds
          className="mc-ambient-canvas"
          speed={0.12}
          cover={0.08}
          density={1.3}
          opacity={0.12}
          color={[0.72, 0.8, 0.71]}
          shadow={0}
          wind={0}
          refraction={0}
          fogBlur={0}
          quality={0.5}
        >
          <div className="mc-ambient-surface" />
        </Clouds>
      ) : null}
    </div>
  );
}
