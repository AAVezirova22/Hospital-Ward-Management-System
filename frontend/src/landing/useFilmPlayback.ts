"use client";

import {
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type RefObject,
} from "react";
import { useInView, useMotionValue } from "motion/react";
import { gsap } from "gsap";
import { useGSAP } from "@gsap/react";
import { useSiteMotion } from "./SiteMotion";

gsap.registerPlugin(useGSAP);

function subscribeVisibility(notify: () => void) {
  document.addEventListener("visibilitychange", notify);
  return () => document.removeEventListener("visibilitychange", notify);
}

/** GSAP owns the clock; Motion alone writes the visual transforms. */
export function useFilmPlayback(
  target: RefObject<HTMLElement | null>,
  duration = 8,
) {
  const { ready, reduced } = useSiteMotion();
  const visible = useInView(target, { amount: 0.35 });
  const documentVisible = useSyncExternalStore(
    subscribeVisibility,
    () => !document.hidden,
    () => false,
  );
  const progress = useMotionValue(0);
  const timeline = useRef<gsap.core.Timeline | null>(null);
  const [paused, setPaused] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [take, setTake] = useState(0);
  const playing = ready && !reduced && visible && documentVisible && !paused;

  useGSAP(
    () => {
      if (!ready || reduced) {
        progress.set(1);
        return;
      }
      const clock = { value: 0 };
      progress.set(0);
      setCompleted(false);
      const film = gsap.timeline({
        paused: true,
        onUpdate: () => progress.set(clock.value),
        onComplete: () => setCompleted(true),
      });
      film
        .to(clock, {
          value: 0.2,
          duration: duration * 0.16,
          ease: "power2.inOut",
        })
        .to(clock, { value: 0.62, duration: duration * 0.5, ease: "none" })
        .to(clock, {
          value: 0.95,
          duration: duration * 0.24,
          ease: "power2.inOut",
        })
        .to(clock, { value: 1, duration: duration * 0.1, ease: "none" });
      timeline.current = film;
      return () => {
        timeline.current = null;
      };
    },
    {
      scope: target,
      dependencies: [ready, reduced, take, duration],
      revertOnUpdate: true,
    },
  );

  useEffect(() => {
    if (playing) timeline.current?.play();
    else timeline.current?.pause();
  }, [playing, take, reduced, ready, duration]);

  return {
    progress,
    paused,
    completed,
    playing: playing && !completed,
    toggle: () => setPaused((value) => !value),
    replay: () => {
      setPaused(false);
      setTake((value) => value + 1);
    },
  };
}
