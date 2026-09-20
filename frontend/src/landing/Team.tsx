"use client";

import { still } from "./media";

const TEAM = [
  {
    key: "mira",
    name: "Dr. Mira Veleva",
    role: "Interventional cardiology",
  },
  {
    key: "asen",
    name: "Dr. Asen Dragomirov",
    role: "Neurosurgery",
  },
  {
    key: "nia",
    name: "Dr. Nia Kolarova",
    role: "Medical oncology",
  },
  {
    key: "luka",
    name: "Dr. Luka Petrov",
    role: "Trauma orthopedics",
  },
];

export function Team() {
  return (
    <section className="film-team" id="team" aria-labelledby="team-title">
      <h2 id="team-title">Meet your care team</h2>
      <div className="film-portraits">
        {TEAM.map((person) => (
          <article className="film-portrait" key={person.key}>
            <figure>
              <img
                src={still(person.key)}
                alt={person.name}
                width={720}
                height={960}
              />
              <figcaption>
                <strong>{person.name}</strong>
                <em>{person.role}</em>
              </figcaption>
            </figure>
          </article>
        ))}
      </div>
    </section>
  );
}
