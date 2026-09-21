"use client";
import {
  createContext,
  useContext,
  useRef,
  useEffect,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { MotionConfig, motion, useAnimate, useInView } from "motion/react";
import { FilmChoreography } from "./FilmChoreography";

const motionQuery = "(prefers-reduced-motion: reduce)";
function subscribeMotion(onChange: () => void) {
  const media = window.matchMedia(motionQuery);
  media.addEventListener("change", onChange);
  return () => media.removeEventListener("change", onChange);
}
const readMotion = () => window.matchMedia(motionQuery).matches;
const serverMotion = () => true;

const MotionPreferences = createContext({
  paused: false,
  reduced: false,
  ready: false,
  toggle: () => {},
});
export const useSiteMotion = () => useContext(MotionPreferences);

export function LandingExperience({
  children,
  nonce,
}: {
  children: ReactNode;
  nonce?: string;
}) {
  const root = useRef<HTMLDivElement>(null);
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
        reduced: !ready || Boolean(reduced) || paused,
        ready,
        toggle,
      }}
    >
      <MotionConfig nonce={nonce} reducedMotion={paused ? "always" : "user"}>
        <div
          ref={root}
          className="mc"
          data-motion={!ready || paused || reduced ? "reduced" : "full"}
          data-ready={ready}
        >
          <FilmChoreography root={root} active={ready && !reduced && !paused} />
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
  const { reduced, ready } = useSiteMotion();
  const [scope, animate] = useAnimate();
  const visible = useInView(scope, { once: true, amount: 0.15 });
  const revealed = useRef(false);
  useEffect(() => {
    if (!ready) return;
    if (reduced) {
      animate(scope.current, { opacity: 1, y: 0 }, { duration: 0 });
      return;
    }
    if (revealed.current) return;
    if (!visible) {
      animate(scope.current, { opacity: 0, y: 36 }, { duration: 0 });
      return;
    }
    revealed.current = true;
    const entrance = animate(
      scope.current,
      { opacity: [0, 1], y: [36, 0] },
      {
        duration: 0.95,
        delay,
        ease: [0.22, 1, 0.36, 1],
      },
    );
    return () => entrance.stop();
  }, [ready, reduced, visible, delay, animate, scope]);
  return (
    <motion.div ref={scope} className={className} data-reveal>
      {children}
    </motion.div>
  );
}
