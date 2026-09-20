"use client";

import dynamic from "next/dynamic";
import { ArrowRight, CaretDown } from "@phosphor-icons/react";
import { FilmMedia } from "./FilmMedia";
import { still } from "./media";
import { useGpuScene } from "./gpu";

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
    <section className="film-hero" id="care" aria-label="Medcore">
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
        <h1>
          <span className="film-title-line">Advanced care.</span>
          <span className="film-title-line">Human at heart.</span>
        </h1>
        <p className="film-hero-lede">
          Specialist care, modern diagnostics, and treatment built around you.
        </p>
        <div className="film-ctas">
          <a className="film-btn" href="#booking" data-film-book="true">
            Book an Appointment
            <span className="film-btn-ico" aria-hidden="true">
              <ArrowRight size={14} weight="light" />
            </span>
          </a>
          <a className="film-text-link" href="#team">
            Find a Specialist
            <ArrowRight size={16} weight="light" />
          </a>
        </div>
      </div>
      <div className="film-hero-floor">
        <a href="#visit">24/7 Emergency Care</a>
        <a
          className="film-hero-down"
          href="#numbers"
          aria-label="Continue to care"
        >
          <CaretDown size={18} weight="light" />
        </a>
        <span>01 / 05</span>
      </div>
    </section>
  );
}
