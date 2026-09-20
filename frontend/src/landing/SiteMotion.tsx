"use client";
import {
  createContext,
  useContext,
  useEffect,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { MotionConfig, motion } from "motion/react";

const motionQuery = "(prefers-reduced-motion: reduce)";
function subscribeMotion(onChange: () => void) {
  const media = window.matchMedia(motionQuery);
  media.addEventListener("change", onChange);
  return () => media.removeEventListener("change", onChange);
}
const readMotion = () => window.matchMedia(motionQuery).matches;
const serverMotion = () => false;

const MotionPreferences = createContext({
  paused: false,
  reduced: false,
  ready: false,
  toggle: () => {},
});
export const useSiteMotion = () => useContext(MotionPreferences);

export function LandingExperience({ children }: { children: ReactNode }) {
  const reduced = useSyncExternalStore(
    subscribeMotion,
    readMotion,
    serverMotion,
  );
  const [paused, setPaused] = useState(false);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    setReady(true);
    try {
      setPaused(localStorage.getItem("medcore-site-motion") === "paused");
    } catch {}
  }, []);
  const toggle = () =>
    setPaused((value) => {
      try {
        localStorage.setItem(
          "medcore-site-motion",
          value ? "playing" : "paused",
        );
      } catch {}
      return !value;
    });
  return (
    <MotionPreferences.Provider
      value={{
        paused,
        reduced: ready && (Boolean(reduced) || paused),
        ready,
        toggle,
      }}
    >
      <MotionConfig reducedMotion={paused ? "always" : "user"}>
        <div
          className="mc"
          data-motion={ready && (paused || reduced) ? "reduced" : "full"}
          data-ready={ready}
        >
          {children}
        </div>
      </MotionConfig>
    </MotionPreferences.Provider>
  );
}

export function Reveal({
  children,
  className,
  delay = 0,
}: {
  children: ReactNode;
  className?: string;
  delay?: number;
}) {
  const { reduced } = useSiteMotion();
  return (
    <motion.div
      className={className}
      data-reveal
      initial={reduced ? false : { opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.15 }}
      transition={{
        duration: reduced ? 0 : 0.8,
        delay: reduced ? 0 : delay,
        ease: [0.22, 1, 0.36, 1],
      }}
    >
      {children}
    </motion.div>
  );
}
