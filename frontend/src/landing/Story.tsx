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
        <p>
          The assistant drafted the night list. I still had to confirm the beds.
        </p>
        <footer>Night coordinator, Cardiology</footer>
      </blockquote>
    </section>
  );
}
