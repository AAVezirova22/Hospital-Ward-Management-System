"use client";

import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import dynamic from "next/dynamic";
import {
  motion,
  AnimatePresence,
  MotionConfig,
  useInView,
  useIsPresent,
} from "motion/react";
import { Activity, ArrowUpRight, Moon, Sun, Pause, Play } from "./icons";

const ease = [0.22, 1, 0.36, 1] as const;
const Clouds = dynamic(() => import("./vendor/canvasui/Clouds"), {
  ssr: false,
});
const CinemaContext = createContext({
  enabled: false,
  systemReduced: false,
  toggle: () => {},
});
const WorkspaceSceneContext = createContext(false);
export const useCinematicMotion = () => useContext(CinemaContext).enabled;

function subscribeToMotionPreference(onChange: () => void) {
  const query = window.matchMedia("(prefers-reduced-motion: reduce)");
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}
const getMotionPreference = () =>
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

export function CinematicProvider({ children }: { children: ReactNode }) {
  // React to OS changes immediately, including an already-open workspace.
  const systemReduced = useSyncExternalStore(
    subscribeToMotionPreference,
    getMotionPreference,
    () => true,
  );
  const [paused, setPaused] = useState(false);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    try {
      setPaused(localStorage.getItem("medcore-motion") === "paused");
    } catch {}
    setReady(true);
  }, []);
  const enabled = ready && !paused && !systemReduced;
  useEffect(() => {
    document.documentElement.dataset.motion = enabled ? "on" : "off";
  }, [enabled]);
  return (
    <CinemaContext.Provider
      value={{
        enabled,
        systemReduced: Boolean(systemReduced),
        toggle: () => {
          const next = !paused;
          setPaused(next);
          try {
            localStorage.setItem("medcore-motion", next ? "paused" : "playing");
          } catch {}
        },
      }}
    >
      <MotionConfig
        reducedMotion={enabled ? "user" : "always"}
        transition={{ duration: enabled ? 0.5 : 0, ease }}
      >
        {children}
      </MotionConfig>
    </CinemaContext.Provider>
  );
}

export function MotionToggle() {
  const { enabled, systemReduced, toggle } = useContext(CinemaContext);
  const label = systemReduced
    ? "Motion reduced by system preference"
    : enabled
      ? "Pause cinematic motion"
      : "Play cinematic motion";
  return (
    <button
      className="icon motion-toggle"
      aria-label={label}
      title={label}
      aria-pressed={enabled}
      disabled={systemReduced}
      onClick={toggle}
    >
      {enabled ? <Pause size={16} /> : <Play size={16} />}
    </button>
  );
}

/** Only the artwork gets GPU treatment; no clinical text is captured or distorted. */
export function Atmosphere() {
  const { enabled } = useContext(CinemaContext);
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref);
  const [available, setAvailable] = useState(false);
  const [idle, setIdle] = useState(false);
  useEffect(() => {
    if ("requestIdleCallback" in window) {
      const id = window.requestIdleCallback(() => setIdle(true), {
        timeout: 3000,
      });
      return () => window.cancelIdleCallback(id);
    }
    const timer = setTimeout(() => setIdle(true), 2500);
    return () => clearTimeout(timer);
  }, []);
  useEffect(() => {
    const screen = matchMedia("(min-width: 801px)");
    const update = () => setAvailable(!document.hidden && screen.matches);
    // Let the image and sign-in controls paint before loading the decorative shader.
    const timer = window.setTimeout(update, 1000);
    document.addEventListener("visibilitychange", update);
    screen.addEventListener("change", update);
    return () => {
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", update);
      screen.removeEventListener("change", update);
    };
  }, []);
  return (
    <div
      ref={ref}
      className="cinema-atmosphere"
      aria-hidden="true"
      data-canvas-ui="clouds"
    >
      {enabled && idle && available && inView && (
        <Clouds
          className="cinema-clouds"
          color={[0.64, 0.76, 0.66]}
          speed={0.2}
          scale={1.3}
          cover={0.08}
          density={1.8}
          opacity={0.24}
          shadow={0}
          quality={0.35}
        >
          <div className="atmosphere-field" />
        </Clouds>
      )}
    </div>
  );
}

/**
 * One route's content. The outgoing scene stays mounted while it animates away, so it is marked
 * inert: assistive technology, keyboard focus and role queries then only ever see the scene that
 * the route actually points at, instead of two copies of every heading and action.
 */
function Scene({
  children,
  animated,
}: {
  children: ReactNode;
  animated: boolean;
}) {
  const present = useIsPresent();
  return (
    <motion.div
      className="scene-content"
      aria-hidden={present ? undefined : true}
      inert={present ? undefined : true}
      initial={false}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={animated ? { opacity: 0, x: -8, scale: 0.996 } : undefined}
      transition={
        animated
          ? { type: "spring", stiffness: 380, damping: 36, mass: 0.55 }
          : { duration: 0 }
      }
    >
      {children}
    </motion.div>
  );
}

export function SceneTransition({
  children,
  scene,
}: {
  children: ReactNode;
  scene: string;
}) {
  const { enabled } = useContext(CinemaContext);
  return (
    <WorkspaceSceneContext.Provider value={true}>
      <AnimatePresence mode="popLayout" initial={false}>
        <Scene key={scene} animated={enabled}>
          {children}
        </Scene>
      </AnimatePresence>
    </WorkspaceSceneContext.Provider>
  );
}

export function Reveal({
  children,
  className = "",
  delay = 0,
}: {
  children: ReactNode;
  className?: string;
  delay?: number;
}) {
  const reduced = !useContext(CinemaContext).enabled;
  const inWorkspace = useContext(WorkspaceSceneContext);
  // Menu changes have one quiet transition; child panels appear together.
  if (inWorkspace) return <div className={className}>{children}</div>;
  return (
    <motion.div
      className={className}
      initial={reduced ? false : { opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.08 }}
      transition={{
        duration: reduced ? 0 : 0.75,
        delay: reduced ? 0 : delay,
        ease,
      }}
    >
      {children}
    </motion.div>
  );
}

export function ThemeToggle() {
  const [theme, setTheme] = useState("dark");
  useEffect(() => {
    let saved: string | null = null;
    try {
      saved = localStorage.getItem("medcore-theme");
    } catch {}
    const initial =
      saved ||
      (matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");
    document.documentElement.dataset.theme = initial;
    setTheme(initial);
  }, []);
  return (
    <button
      className="icon theme-toggle"
      aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}
      onClick={() => {
        const next = theme === "dark" ? "light" : "dark";
        setTheme(next);
        document.documentElement.dataset.theme = next;
        try {
          localStorage.setItem("medcore-theme", next);
        } catch {}
      }}
    >
      {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
    </button>
  );
}

export function MagneticButton({
  children,
  onClick,
  className = "primary",
}: {
  children: ReactNode;
  onClick: () => void;
  className?: string;
}) {
  const reduced = !useContext(CinemaContext).enabled;
  const magnet = useRef<HTMLButtonElement>(null);
  return (
    <button
      ref={magnet}
      className={className}
      onClick={onClick}
      onPointerMove={(event) => {
        if (reduced || event.pointerType !== "mouse" || !magnet.current) return;
        const bounds = event.currentTarget.getBoundingClientRect();
        const x = (event.clientX - bounds.left - bounds.width / 2) * 0.07;
        const y = (event.clientY - bounds.top - bounds.height / 2) * 0.12;
        magnet.current.style.transform = `translate(${x}px, ${y}px)`;
      }}
      onPointerLeave={() => {
        if (magnet.current) magnet.current.style.transform = "";
      }}
    >
      {children}
      <span className="button-icon">
        <ArrowUpRight size={17} />
      </span>
    </button>
  );
}
