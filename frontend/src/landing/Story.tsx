"use client";

import { FilmMedia } from "./FilmMedia";
import { still } from "./media";

export function Story() {
  return (
    <section className="film-story" aria-label="Patient story">
      <FilmMedia
        still={still("patient")}
        alt="Kalina Ruseva sitting by a hospital window in morning light"
      />
      <div className="film-story-shade" />
      <blockquote>
        <p>They treated the scan, then they treated me as a person.</p>
        <footer>Kalina Ruseva, spinal recovery</footer>
      </blockquote>
    </section>
  );
}
