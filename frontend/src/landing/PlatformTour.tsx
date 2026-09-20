"use client";
import { useState, useRef, type KeyboardEvent } from "react";
import Image from "next/image";
import Link from "next/link";
import { AnimatePresence, motion } from "motion/react";
import {
  ArrowUpRight,
  Heartbeat,
  Bed,
  ChartLineUp,
  UsersThree,
} from "@phosphor-icons/react";
import { Reveal, useSiteMotion } from "./SiteMotion";

const panels = [
  {
    title: "The whole ward, at a glance.",
    name: "Ward overview",
    icon: Heartbeat,
    copy: "One shared picture of admissions, capacity, and activity. Start every shift knowing where things stand.",
    link: "/app/dashboard",
    position: "top",
  },
  {
    title: "A place for every patient.",
    name: "Patients & beds",
    icon: Bed,
    copy: "Follow admissions through transfers and discharge. Plan room moves and check capacity before confirming a change.",
    link: "/app/planner",
    position: "bottom",
  },
  {
    title: "Everyone, on the same page.",
    name: "Your care team",
    icon: UsersThree,
    copy: "Bring doctors and staff into the right department. Keep permissions, assignments, and care history connected.",
    link: "/app/doctors",
    position: "center",
  },
  {
    title: "Know what the day adds up to.",
    name: "Reports & activity",
    icon: ChartLineUp,
    copy: "Explore occupancy and procedure reports, filter by room or doctor, and take the numbers with you in a CSV export.",
    link: "/app/reports",
    position: "top",
  },
];

export function PlatformTour() {
  const [selected, setSelected] = useState(0);
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const { reduced } = useSiteMotion();
  const panel = panels[selected];
  const onKey = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let next = index;
    if (event.key === "ArrowRight") next = (index + 1) % panels.length;
    else if (event.key === "ArrowLeft")
      next = (index - 1 + panels.length) % panels.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = panels.length - 1;
    else return;
    event.preventDefault();
    setSelected(next);
    buttons.current[next]?.focus();
  };
  return (
    <section
      className="mc-platform"
      id="platform"
      aria-labelledby="platform-title"
    >
      <div className="mc-wrap">
        <Reveal>
          <p className="mc-eyebrow">One connected platform</p>
          <h2 id="platform-title">
            Everything in view.
            <br />
            <span className="mc-muted">Everyone in sync.</span>
          </h2>
        </Reveal>
        <div
          className="mc-platform-tabs"
          role="tablist"
          aria-label="Explore the platform"
        >
          {panels.map((item, index) => (
            <button
              key={item.name}
              id={`platform-tab-${index}`}
              ref={(node) => {
                buttons.current[index] = node;
              }}
              role="tab"
              aria-selected={index === selected}
              aria-controls="platform-panel"
              tabIndex={index === selected ? 0 : -1}
              onKeyDown={(event) => onKey(event, index)}
              onClick={() => setSelected(index)}
            >
              <item.icon size={20} weight="light" aria-hidden="true" />
              {item.name}
              {index === selected && (
                <motion.span
                  className="mc-tab-line"
                  layoutId={reduced ? undefined : "platform-tab-line"}
                  transition={{ type: "spring", stiffness: 360, damping: 34 }}
                />
              )}
            </button>
          ))}
        </div>
        <div
          id="platform-panel"
          role="tabpanel"
          aria-labelledby={`platform-tab-${selected}`}
          className="mc-platform-panel"
          tabIndex={0}
        >
          <div className="mc-platform-copy">
            <AnimatePresence mode="wait">
              <motion.div
                key={selected}
                initial={reduced ? false : { opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: reduced ? 0 : 0.2 }}
              >
                <panel.icon
                  className="mc-feature-icon"
                  size={36}
                  weight="light"
                  aria-hidden="true"
                />
                <h3>{panel.title}</h3>
                <p>{panel.copy}</p>
                <Link href={panel.link} className="mc-text-link">
                  Explore in Medcore <ArrowUpRight aria-hidden="true" />
                </Link>
              </motion.div>
            </AnimatePresence>
            <span className="mc-product-caption">
              Actual Medcore workspace
              <br />
              Shown with demonstration data
            </span>
          </div>
          <div className="mc-product-bezel">
            <div className="mc-product-window">
              <div className="mc-window-top">
                <span>Medcore workspace</span>
                <span>Department overview</span>
              </div>
              <Image
                src="/landing/stills/workspace.webp"
                alt="Actual Medcore dashboard with admissions, bed availability, staff counts, and room capacity using demonstration data"
                width={1440}
                height={1593}
                sizes="(max-width: 767px) 94vw, 68vw"
                style={{ objectPosition: panel.position }}
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
