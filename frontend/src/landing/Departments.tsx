"use client";

import { FilmMedia } from "./FilmMedia";
import { clip, still } from "./media";

const DEPTS = [
  {
    key: "cardiology",
    title: "Cardiology",
    copy: "Quiet rooms for hearts that need a longer look, from imaging to the catheter lab.",
  },
  {
    key: "neurology",
    title: "Neurology",
    copy: "Stroke, memory, and surgical planning read from the same set of scans.",
  },
  {
    key: "oncology",
    title: "Oncology",
    copy: "Day treatment with daylight, and a specialist who stays in the conversation.",
  },
  {
    key: "orthopedics",
    title: "Orthopedics",
    copy: "Joints, trauma, and the slow work of walking again under morning windows.",
  },
];

export function Departments() {
  return (
    <section className="film-departments" id="specialists">
      <h2 className="film-kicker">Departments</h2>
      <div className="film-dept-track">
        {DEPTS.map((dept) => (
          <article className="film-dept" key={dept.key}>
            <FilmMedia
              still={still(dept.key)}
              video={clip(dept.key)}
              alt={`${dept.title} at Medcore`}
            />
            <div className="film-dept-meta">
              <h3>{dept.title}</h3>
              <p>{dept.copy}</p>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
