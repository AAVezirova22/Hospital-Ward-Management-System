"use client";

import { useEffect, useState } from "react";
import { useReducedMotion } from "motion/react";

export function useGpuScene() {
  const reduce = useReducedMotion();
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
  return Boolean(!reduce && wide);
}
