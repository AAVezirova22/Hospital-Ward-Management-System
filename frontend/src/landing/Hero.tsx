"use client";

import { ArrowRight, CaretDown } from "@phosphor-icons/react";
import { FilmMedia } from "./FilmMedia";
import { clip, still } from "./media";
import { Mist } from "./Mist";

export function Hero() {
  return (
    <section className="film-hero" id="care" aria-label="Medcore">
      <FilmMedia
        still={still("hero")}
        video={clip("hero")}
        alt="A doctor walking a quiet hospital corridor at sunrise"
        priority
      />
      <div className="film-hero-shade" />
      <Mist />
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
