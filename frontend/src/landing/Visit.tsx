"use client";

import Link from "next/link";
import { ArrowRight } from "@phosphor-icons/react";
import { FilmMedia } from "./FilmMedia";
import { still } from "./media";

export function Visit() {
  return (
    <section className="film-visit" id="start">
      <h2>Open the ward</h2>
      <div className="film-visit-grid">
        <div>
          <p className="film-live-kicker">Staff workspace</p>
          <p>
            Sign in to the department you belong to. Invite codes grant hospital
            membership or department staff access, never administrator rights by
            accident. Patients see only their own history.
          </p>
          <div className="film-visit-actions">
            <Link className="film-btn" href="/app">
              Open the workspace
              <span className="film-btn-ico" aria-hidden="true">
                <ArrowRight size={14} weight="light" />
              </span>
            </Link>
            <a className="film-text-link" href="#booking" data-film-book="true">
              Request a walkthrough
              <ArrowRight size={16} weight="light" />
            </a>
          </div>
        </div>
        <FilmMedia still={still("entrance")} alt="Medcore entrance at night" />
      </div>
    </section>
  );
}
