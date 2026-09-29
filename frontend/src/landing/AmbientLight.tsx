"use client";

import { useRef } from "react";
import dynamic from "next/dynamic";
import { useInView } from "motion/react";
import { useGpuScene } from "./gpu";
import { useSiteMotion } from "./SiteMotion";

const Clouds = dynamic(() => import("../vendor/canvasui/Clouds"), {
  ssr: false,
});

/**
 * Canvas UI's WebGL overlay: artwork only, no experimental DOM capture required.
 * A slow haze of window light drifts through the frame; the cursor's wind
 * parts it, so the reader's presence visibly clears the scene.
 */
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
          scale={1.4}
          speed={0.22}
          cover={0.22}
          density={1.7}
          shading={0.14}
          opacity={0.38}
          color={[0.95, 0.98, 0.92]}
          shadow={0}
          wind={0.55}
          windRadius={240}
          refraction={0}
          fogBlur={0}
          quality={0.55}
        >
          <div className="mc-ambient-surface" />
        </Clouds>
      ) : null}
    </div>
  );
}
