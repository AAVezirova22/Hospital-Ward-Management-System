"use client";

import { ArrowRight, Phone } from "@phosphor-icons/react";
import { FilmMedia } from "./FilmMedia";
import { still } from "./media";

export function Visit() {
  return (
    <section className="film-visit" id="visit">
      <h2>If you need us now</h2>
      <div className="film-visit-grid">
        <div>
          <a className="film-phone" href="tel:+35924031911">
            <Phone size={22} weight="light" />
            +359 2 403 1911
          </a>
          <p>
            Emergency desk, open through the night. For a planned visit, book a
            time. For directions: 18 Shipchenski Prohod Blvd, Sofia.
          </p>
          <div className="film-visit-actions">
            <a className="film-btn" href="#booking">
              Book an Appointment
              <span className="film-btn-ico" aria-hidden="true">
                <ArrowRight size={14} weight="light" />
              </span>
            </a>
            <a
              className="film-text-link"
              href="https://maps.google.com/?q=18+Shipchenski+Prohod+Blvd+Sofia"
            >
              Directions
              <ArrowRight size={16} weight="light" />
            </a>
          </div>
        </div>
        <FilmMedia still={still("entrance")} alt="Medcore entrance at night" />
      </div>
    </section>
  );
}
