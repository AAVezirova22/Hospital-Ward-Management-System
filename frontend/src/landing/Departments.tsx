"use client";

import dynamic from "next/dynamic";
import { FilmMedia } from "./FilmMedia";
import { still } from "./media";
import { useGpuScene } from "./gpu";

const Peel = dynamic(() => import("../vendor/canvasui/Peel"), { ssr: false });

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
  const gpu = useGpuScene();
  return (
    <section className="film-departments" id="specialists">
      <h2 className="film-kicker">Departments</h2>
      <div className="film-dept-track">
        {DEPTS.map((dept) => {
          const face = (
            <>
              <FilmMedia
                still={still(dept.key)}
                alt={`${dept.title} at Medcore`}
              />
              <div className="film-dept-meta">
                <h3>{dept.title}</h3>
                <p>{dept.copy}</p>
              </div>
            </>
          );
          const under = (
            <div className="film-dept-under">
              <strong>{dept.title}</strong>
              <p>{dept.copy}</p>
            </div>
          );
          return gpu ? (
            <Peel
              className="film-dept"
              key={dept.key}
              side="bottom"
              mode="hover"
              reveal={260}
              zone={160}
              curl={180}
              bow={40}
              shade={0.2}
              shine={0.35}
              under={under}
            >
              {face}
            </Peel>
          ) : (
            <article className="film-dept" key={dept.key}>
              {face}
            </article>
          );
        })}
      </div>
    </section>
  );
}
