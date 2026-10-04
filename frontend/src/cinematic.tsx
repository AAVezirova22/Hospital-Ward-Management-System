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

// Mirrors the matchMedia subscription above so `paused` is also resolved
// synchronously on the first client render via useSyncExternalStore,
// instead of a `ready` flag flipped in an effect after mount. A motion
// component's `initial` prop (and MotionConfig's `reducedMotion`) is only
// ever resolved at that component's first render, so any gap between
// "mounted" and "ready" risked permanently locking a component into its
// reduced-motion starting state even once motion was confirmed on.
const pausedListeners = new Set<() => void>();
function subscribeToPaused(onChange: () => void) {
  pausedListeners.add(onChange);
  return () => pausedListeners.delete(onChange);
}
function getPaused() {
  try {
    return localStorage.getItem("medcore-motion") === "paused";
  } catch {
    return false;
  }
}
function setPausedPreference(next: boolean) {
  try {
    localStorage.setItem("medcore-motion", next ? "paused" : "playing");
  } catch {}
  pausedListeners.forEach((listener) => listener());
}

export function CinematicProvider({
  children,
  nonce,
}: {
  children: ReactNode;
  nonce?: string;
}) {
  // React to OS changes immediately, including an already-open workspace.
  const systemReduced = useSyncExternalStore(
    subscribeToMotionPreference,
    getMotionPreference,
    () => true,
  );
  const paused = useSyncExternalStore(
    subscribeToPaused,
    getPaused,
    () => false,
  );
  const enabled = !paused && !systemReduced;
  useEffect(() => {
    document.documentElement.dataset.motion = enabled ? "on" : "off";
  }, [enabled]);
  return (
    <CinemaContext.Provider
      value={{
        enabled,
        systemReduced: Boolean(systemReduced),
        toggle: () => setPausedPreference(!paused),
      }}
    >
      <MotionConfig
        nonce={nonce}
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
        <motion.div
          key={scene}
          className="scene-content"
          initial={false}
          animate={{ opacity: 1, x: 0, scale: 1 }}
          exit={enabled ? { opacity: 0, x: -8, scale: 0.996 } : undefined}
          transition={
            enabled
              ? { type: "spring", stiffness: 380, damping: 36, mass: 0.55 }
              : { duration: 0 }
          }
        >
          {children}
        </motion.div>
      </AnimatePresence>
    </WorkspaceSceneContext.Provider>
  );
}

export function Reveal({
  children,
  className = "",
  delay = 0,
  force = false,
}: {
  children: ReactNode;
  className?: string;
  delay?: number;
  // Opts out of the workspace bypass below, for a page that wants its
  // sections to genuinely animate in as the viewer scrolls (e.g. Overview),
  // rather than the one quiet transition SceneTransition already gives
  // every route change.
  force?: boolean;
}) {
  const reduced = !useContext(CinemaContext).enabled;
  const inWorkspace = useContext(WorkspaceSceneContext);
  // Menu changes have one quiet transition; child panels appear together,
  // unless the caller asks for a real scroll reveal via `force`.
  const bypass = inWorkspace && !force;
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    if (bypass || reduced || visible) return;
    const node = ref.current;
    if (!node) return;
    // A plain IntersectionObserver rather than motion's whileInView/viewport
    // props: it's one less layer between "the section is on screen" and
    // "it animates in", and is simple enough to reason about directly.
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { threshold: 0.08 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [bypass, reduced, visible]);
  if (bypass) return <div className={className}>{children}</div>;
  const shown = reduced || visible;
  return (
    // data-shown lets CSS cascade the section's own cards, bars and rows in
    // once the section itself is on screen (see design.css).
    <motion.div
      ref={ref}
      className={className}
      data-reveal=""
      data-shown={shown ? "" : undefined}
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: shown ? 1 : 0, y: shown ? 0 : 24 }}
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
