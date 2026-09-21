"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { ArrowRight, CaretDown } from "@phosphor-icons/react";
import { FilmMedia } from "./FilmMedia";
import { still } from "./media";
import { useGpuScene } from "./gpu";
import { Pulse } from "./Pulse";

const Liquid = dynamic(() => import("../vendor/canvasui/Liquid"), {
  ssr: false,
});

export function Hero() {
  const gpu = useGpuScene();
  const photo = (
    <FilmMedia
      still={still("hero")}
      alt="A doctor walking a quiet hospital corridor at sunrise"
      priority
    />
  );
  return (
    <section className="film-hero" id="product" aria-label="Medcore">
      {gpu ? (
        <Liquid
          className="film-hero-gpu"
          style={{ position: "absolute", inset: 0 }}
          color={[0.36, 0.53, 0.63]}
          rainbow={false}
          force={0.55}
          radius={0.42}
          curl={1.1}
          intensity={0.55}
          distortion={0.18}
          blend={1.4}
          densityDissipation={0.98}
        >
          {photo}
        </Liquid>
      ) : (
        photo
      )}
      <div className="film-hero-shade" />
      <div className="film-hero-copy">
        <Pulse />
        <h1>
          <span className="film-title-line">The ward,</span>
          <span className="film-title-line">in the moment.</span>
        </h1>
        <p className="film-hero-lede">
          Hospital operations with an assistant that plans from files and
          Messages, then waits for a human to confirm.
        </p>
        <div className="film-ctas">
          <Link className="film-btn" href="/app">
            Open the workspace
            <span className="film-btn-ico" aria-hidden="true">
              <ArrowRight size={14} weight="light" />
            </span>
          </Link>
          <a className="film-text-link" href="#assistant">
            See the assistant
            <ArrowRight size={16} weight="light" />
          </a>
        </div>
      </div>
      <div className="film-hero-floor">
        <a href="#workflow">iMessage and files</a>
        <a
          className="film-hero-down"
          href="#assistant"
          aria-label="Continue to the assistant"
        >
          <CaretDown size={18} weight="light" />
        </a>
        <span>01 / 05</span>
      </div>
    </section>
  );
}
