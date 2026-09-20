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
  MotionConfig,
  useInView,
  useMotionValue,
  useSpring,
  useScroll,
  useTransform,
} from "motion/react";
import { Activity, ArrowUpRight, Moon, Sun, Pause, Play } from "lucide-react";

const ease = [0.22, 1, 0.36, 1] as const;
const Clouds = dynamic(() => import("./components/canvasui/Clouds"), {
  ssr: false,
});
const CinemaContext = createContext({
  enabled: false,
  systemReduced: false,
  toggle: () => {},
});
const WorkspaceSceneContext = createContext(false);

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
function Atmosphere() {
  const { enabled } = useContext(CinemaContext);
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref);
  const [available, setAvailable] = useState(false);
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
      {enabled && available && inView && (
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
      <motion.div
        key={scene}
        className="scene-content"
        initial={enabled ? { opacity: 0.86 } : false}
        animate={{ opacity: 1 }}
        transition={{
          duration: enabled ? 0.18 : 0,
          ease: [0.25, 0.1, 0.25, 1],
        }}
      >
        {children}
      </motion.div>
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
  const x = useMotionValue(0),
    y = useMotionValue(0);
  const sx = useSpring(x, { stiffness: 250, damping: 22 });
  const sy = useSpring(y, { stiffness: 250, damping: 22 });
  return (
    <motion.button
      className={className}
      onClick={onClick}
      style={{ x: reduced ? 0 : sx, y: reduced ? 0 : sy }}
      whileTap={reduced ? undefined : { scale: 0.97 }}
      onPointerMove={(event) => {
        if (reduced || event.pointerType !== "mouse") return;
        const bounds = event.currentTarget.getBoundingClientRect();
        x.set((event.clientX - bounds.left - bounds.width / 2) * 0.07);
        y.set((event.clientY - bounds.top - bounds.height / 2) * 0.12);
      }}
      onPointerLeave={() => {
        x.set(0);
        y.set(0);
      }}
    >
      {children}
      <span className="button-icon">
        <ArrowUpRight size={17} />
      </span>
    </motion.button>
  );
}

export function LoginScene({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const reduced = !useContext(CinemaContext).enabled;
  const pointerX = useMotionValue(0),
    pointerY = useMotionValue(0);
  const cameraX = useSpring(pointerX, { stiffness: 45, damping: 25 });
  const cameraY = useSpring(pointerY, { stiffness: 45, damping: 25 });
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start start", "end start"],
  });
  const y = useTransform(scrollYProgress, [0, 1], [0, 80]);
  return (
    <div className="login-scene" ref={ref}>
      <a className="skip-link" href="#sign-in">
        Skip to sign in
      </a>
      <header className="login-nav">
        <a className="brand" href="/" aria-label="Medcore home">
          <span className="brandmark">
            <Activity size={23} strokeWidth={1.5} />
          </span>
          medcore<span className="brand-dot">®</span>
        </a>
        <div>
          <span>Hospital operations</span>
          <MotionToggle />
          <ThemeToggle />
        </div>
      </header>
      <main className="login-stage">
        <section
          className="login-visual"
          aria-label="Your connected department"
          onPointerMove={(event) => {
            if (reduced || event.pointerType !== "mouse") return;
            const bounds = event.currentTarget.getBoundingClientRect();
            pointerX.set(
              ((event.clientX - bounds.left) / bounds.width - 0.5) * -20,
            );
            pointerY.set(
              ((event.clientY - bounds.top) / bounds.height - 0.5) * -14,
            );
          }}
          onPointerLeave={() => {
            pointerX.set(0);
            pointerY.set(0);
          }}
        >
          <motion.div
            className="cinema-camera"
            style={{ x: reduced ? 0 : cameraX, y: reduced ? 0 : cameraY }}
          >
            <motion.div
              className="atrium-image"
              style={{ y: reduced ? 0 : y }}
            />
          </motion.div>
          <Atmosphere />
          <div className="atrium-shade" />
          <div className="cinema-grain" aria-hidden="true" />
          <div className="scene-heading" aria-hidden="true">
            <span>MEDCORE / A CONNECTED VIEW</span>
            <span className="scene-rule" />
          </div>
          <div className="cinema-aperture aperture-top" aria-hidden="true" />
          <div className="cinema-aperture aperture-bottom" aria-hidden="true" />
          <div className="login-story">
            <Reveal>
              <span className="story-kicker">Clarity at every handover</span>
            </Reveal>
            <Reveal delay={0.12}>
              <h1 aria-label="Space to focus. Room to care.">
                <span className="title-mask" aria-hidden="true">
                  <span className="title-line">Space to focus.</span>
                </span>
                <span className="title-mask title-accent" aria-hidden="true">
                  <span className="title-line">Room to care.</span>
                </span>
              </h1>
            </Reveal>
            <Reveal delay={0.24}>
              <p>
                Your people, capacity and care operations.
                <br />
                Together in one considered workspace.
              </p>
            </Reveal>
          </div>
          <Reveal className="login-visual-caption" delay={0.4}>
            <span>Built around your department.</span>
            <Activity size={24} strokeWidth={1} />
          </Reveal>
        </section>
        <section className="login-access" id="sign-in" aria-label="Sign in">
          <Reveal className="login-form-wrap" delay={0.3}>
            {children}
          </Reveal>
          <p className="access-caption">A connected view. A better day.</p>
        </section>
      </main>
    </div>
  );
}

export function OverviewHero({ children }: { children: ReactNode }) {
  return (
    <Reveal className="overview-hero">
      <div className="overview-hero-image" aria-hidden="true" />
      <Atmosphere />
      <div className="cinema-grain" aria-hidden="true" />
      <div className="overview-hero-copy">{children}</div>
      <div className="overview-hero-caption">
        Made for the people
        <br />
        who care for people.
      </div>
    </Reveal>
  );
}
