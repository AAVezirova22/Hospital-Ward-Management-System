"use client";
import { useRef } from "react";
import Image from "next/image";
import Link from "next/link";
import dynamic from "next/dynamic";
import { ArrowUpRight, Play } from "@phosphor-icons/react";
import { motion, useInView, useScroll, useTransform } from "motion/react";
import { useGpuScene } from "./gpu";
import { useSiteMotion } from "./SiteMotion";

const Liquid = dynamic(() => import("../vendor/canvasui/Liquid"), {
  ssr: false,
});

export function PresenceHero() {
  const ref = useRef<HTMLElement>(null);
  const { reduced } = useSiteMotion();
  const gpu = useGpuScene();
  const inView = useInView(ref, { margin: "100px" });
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start start", "end start"],
  });
  const imageY = useTransform(scrollYProgress, [0, 1], [0, 110]);
  const copyY = useTransform(scrollYProgress, [0, 1], [0, -45]);
  return (
    <section className="mc-hero" id="product" ref={ref}>
      <div className="mc-hero-frame">
        <motion.div
          className="mc-hero-image"
          style={{ y: reduced ? 0 : imageY }}
        >
          <Image
            src="/landing/stills/presence.webp"
            alt="Two care professionals reviewing a tablet in a sunlit hospital"
            fill
            sizes="100vw"
            preload
          />
          {gpu && inView && !reduced ? (
            <Liquid
              className="mc-hero-liquid"
              color={[0.42, 0.57, 0.45]}
              rainbow={false}
              force={0.25}
              radius={0.28}
              curl={0.5}
              intensity={0.18}
              distortion={0.08}
              blend={0.35}
            >
              <div className="mc-liquid-surface" />
            </Liquid>
          ) : null}
        </motion.div>
        <div className="mc-hero-wash" />
        <motion.div className="mc-hero-copy" style={{ y: reduced ? 0 : copyY }}>
          <p className="mc-eyebrow">Hospital operations, with a human touch</p>
          <h1>
            <motion.span
              initial={reduced ? false : { y: 28, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ duration: 0.95, ease: [0.22, 1, 0.36, 1] }}
            >
              More presence.
            </motion.span>{" "}
            <motion.span
              initial={reduced ? false : { y: 28, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{
                duration: 0.95,
                delay: 0.12,
                ease: [0.22, 1, 0.36, 1],
              }}
            >
              Less process.
            </motion.span>
          </h1>
          <motion.div
            initial={reduced ? false : { y: 16, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{
              duration: 0.8,
              delay: 0.25,
              ease: [0.22, 1, 0.36, 1],
            }}
          >
            <p className="mc-hero-lede">
              One connected workspace for your ward, your team,
              <br className="mc-desktop" /> and everything that keeps care
              moving.
            </p>
            <div className="mc-actions">
              <Link className="mc-button" href="/app">
                Open the workspace
                <span>
                  <ArrowUpRight size={20} aria-hidden="true" />
                </span>
              </Link>
              <a className="mc-tour-link" href="#booking">
                <span>
                  <Play size={12} weight="fill" aria-hidden="true" />
                </span>
                Meet Medcore
              </a>
            </div>
          </motion.div>
        </motion.div>
        <a className="mc-hero-note" href="#assistant">
          <span>
            Less busywork.
            <br />
            <strong>More room for care.</strong>
          </span>
          <ArrowUpRight size={20} aria-hidden="true" />
        </a>
      </div>
    </section>
  );
}
