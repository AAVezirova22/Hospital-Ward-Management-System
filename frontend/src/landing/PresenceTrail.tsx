"use client";

import { useRef } from "react";
import dynamic from "next/dynamic";
import { useInView } from "motion/react";
import { useGpuScene } from "./gpu";
import { useSiteMotion } from "./SiteMotion";

const Liquid = dynamic(() => import("../vendor/canvasui/Liquid"), {
  ssr: false,
});

/**
 * Canvas UI Liquid behind the closing chapter: the pointer leaves a soft
 * trail of light across the panel, the page's last expression of presence.
 * Artwork only; text and actions stay in normal HTML above it.
 */
export function PresenceTrail() {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref);
  const gpu = useGpuScene();
  const { ready, reduced } = useSiteMotion();
  return (
    <div ref={ref} className="mc-trail" aria-hidden="true">
      {ready && gpu && inView && !reduced ? (
        <Liquid
          className="mc-trail-canvas"
          style={{ position: "absolute", inset: 0 }}
          color={[0.98, 1, 0.95]}
          rainbow={false}
          simResolution={96}
          dyeResolution={384}
          densityDissipation={0.965}
          velocityDissipation={0.6}
          curl={1.4}
          radius={0.24}
          force={0.9}
          intensity={0.9}
        >
          <div className="mc-trail-surface" />
        </Liquid>
      ) : null}
    </div>
  );
}
