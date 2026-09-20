"use client";
import { useRef } from "react";
import Image from "next/image";
import Link from "next/link";
import { ArrowUpRight, Play } from "@phosphor-icons/react";
import { motion, useTransform } from "motion/react";
import { AmbientLight } from "./AmbientLight";
import { useFilmPlayback } from "./useFilmPlayback";
import { useSiteMotion } from "./SiteMotion";

export function PresenceHero() {
  const ref = useRef<HTMLElement>(null);
  const { reduced } = useSiteMotion();
  const film = useFilmPlayback(ref, 18);
  const imageScale = useTransform(film.progress, [0, 1], [1.045, 1]);
  return (
    <section className="mc-hero" id="product" ref={ref}>
      <div className="mc-hero-frame">
        <motion.div
          className="mc-hero-image"
          style={{ scale: reduced ? 1 : imageScale }}
        >
          <Image
            src="/landing/stills/presence.webp"
            alt="Two care professionals reviewing a tablet in a sunlit hospital"
            fill
            sizes="(max-width: 767px) calc(100vw - 24px), (min-width: 1600px) 1556px, calc(100vw - 44px)"
            preload
          />
        </motion.div>
        <AmbientLight />
        <div className="mc-hero-wash" />
        <div className="mc-hero-copy">
          <p className="mc-eyebrow">Hospital operations, with a human touch</p>
          <h1>
            <span>More presence.</span> <span>Less process.</span>
          </h1>
          <div>
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
          </div>
        </div>
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
