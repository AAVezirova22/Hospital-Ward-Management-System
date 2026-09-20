"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  motion,
  useMotionValue,
  useReducedMotion,
  useSpring,
  useScroll,
  useTransform,
} from "motion/react";
import { Activity, ArrowUpRight, Moon, Sun } from "lucide-react";

const ease = [0.22, 1, 0.36, 1] as const;

export function Reveal({
  children,
  className = "",
  delay = 0,
}: {
  children: ReactNode;
  className?: string;
  delay?: number;
}) {
  const reduced = useReducedMotion();
  return (
    <motion.div
      className={className}
      initial={reduced ? false : { opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.08 }}
      transition={{ duration: 0.75, delay, ease }}
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
  const reduced = useReducedMotion();
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
  const reduced = useReducedMotion();
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
          <ThemeToggle />
        </div>
      </header>
      <main className="login-stage">
        <section
          className="login-visual"
          aria-label="Your connected department"
        >
          <motion.div
            className="atrium-image"
            style={{ y: reduced ? 0 : y }}
            initial={reduced ? false : { scale: 1.09 }}
            animate={{ scale: 1 }}
            transition={{ duration: 2.4, ease }}
          />
          <div className="atrium-shade" />
          <div className="login-story">
            <Reveal>
              <span className="story-kicker">Clarity at every handover</span>
            </Reveal>
            <Reveal delay={0.12}>
              <h1>
                Space to focus.
                <br />
                <span>Room to care.</span>
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
      <div className="overview-hero-copy">{children}</div>
      <div className="overview-hero-caption">
        Made for the people
        <br />
        who care for people.
      </div>
    </Reveal>
  );
}
