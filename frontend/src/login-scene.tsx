"use client";

import { useRef } from "react";
import { useMotionValue, useSpring, useScroll, useTransform } from "motion/react";
import { Activity } from "./icons";
import {
  Atmosphere,
  MotionToggle,
  Reveal,
  ThemeToggle,
  useCinematicMotion,
} from "./cinematic";

export function LoginScene({ children }: { children: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const reduced = !useCinematicMotion();
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
          <div
            className="cinema-camera"
            ref={(el) => {
              if (!el) return;
              const apply = () => {
                if (reduced) {
                  el.style.transform = "";
                  return;
                }
                el.style.transform = `translate(${cameraX.get()}px, ${cameraY.get()}px)`;
              };
              apply();
              const ux = cameraX.on("change", apply);
              const uy = cameraY.on("change", apply);
              return () => {
                ux();
                uy();
              };
            }}
          >
            <div
              className="atrium-image"
              ref={(el) => {
                if (!el) return;
                const apply = () => {
                  el.style.transform = reduced ? "" : `translateY(${y.get()}px)`;
                };
                apply();
                return y.on("change", apply);
              }}
            />
          </div>
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
            <Activity size={24} strokeWidth={1.5} />
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
