"use client";

import type { PointerEvent, ReactNode } from "react";
import { motion, useMotionValue, useSpring, useTransform } from "motion/react";
import { useSiteMotion } from "./SiteMotion";

const spring = { stiffness: 220, damping: 18, mass: 0.6 };

/**
 * Pulls its child a few pixels toward a fine pointer. Motion values keep
 * the physics outside React renders; the child's own CSS hover and press
 * states stay intact because only this wrapper is transformed.
 */
export function Magnetic({
  children,
  strength = 0.18,
}: {
  children: ReactNode;
  strength?: number;
}) {
  const { reduced } = useSiteMotion();
  const pullX = useMotionValue(0);
  const pullY = useMotionValue(0);
  const x = useSpring(pullX, spring);
  const y = useSpring(pullY, spring);
  const rotate = useTransform(x, [-24, 24], [-2.5, 2.5]);

  const move = (event: PointerEvent<HTMLSpanElement>) => {
    if (reduced || event.pointerType !== "mouse") return;
    const box = event.currentTarget.getBoundingClientRect();
    pullX.set((event.clientX - (box.left + box.width / 2)) * strength);
    pullY.set((event.clientY - (box.top + box.height / 2)) * strength);
  };
  const release = () => {
    pullX.set(0);
    pullY.set(0);
  };

  return (
    <motion.span
      className="mc-magnetic"
      style={reduced ? undefined : { x, y, rotate }}
      onPointerMove={move}
      onPointerLeave={release}
    >
      {children}
    </motion.span>
  );
}
