"use client";
import { useRef } from "react";
import Image from "next/image";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";
import {
  ArrowUpRight,
  UsersThree,
  Bed,
  ClipboardText,
} from "@phosphor-icons/react";
import { useSiteMotion } from "./SiteMotion";

gsap.registerPlugin(useGSAP, ScrollTrigger);
const chapters = [
  {
    title: "Start with the person.",
    copy: "A patient record brings the essentials together. Admissions, attending doctors, procedures, and care history stay connected.",
    image: "clinical-conversation.webp",
    alt: "A doctor consulting with a patient at her bedside",
    icon: UsersThree,
    label: "Patient records",
    link: "/app/patients",
  },
  {
    title: "Make the next move clearer.",
    copy: "See where there is room. Stage a transfer on the ward map and review available capacity before making it happen.",
    image: "hospital-lobby.webp",
    alt: "Glass doors and trees reflected at a hospital entrance",
    icon: Bed,
    label: "Ward planning",
    link: "/app/planner",
  },
  {
    title: "Carry the context forward.",
    copy: "From admission to discharge, the history stays with the patient. Your next shift starts with a shared understanding.",
    image: "clinical-conversation.webp",
    alt: "A clinician connecting with a patient",
    icon: ClipboardText,
    label: "Care continuity",
    link: "/app/admissions",
  },
];

export function WorkflowStory() {
  const root = useRef<HTMLElement>(null);
  const { reduced, ready } = useSiteMotion();
  useGSAP(
    () => {
      if (!ready || reduced) return;
      const mm = gsap.matchMedia();
      mm.add("(min-width: 1024px)", () => {
        ScrollTrigger.create({
          trigger: ".mc-story-layout",
          start: "top 125px",
          end: "bottom bottom",
          pin: ".mc-story-heading",
          pinSpacing: false,
        });
        gsap.utils.toArray<HTMLElement>(".mc-story-card").forEach((card) => {
          gsap.fromTo(
            card.querySelector("img"),
            { scale: 1.09 },
            {
              scale: 1,
              ease: "none",
              scrollTrigger: {
                trigger: card,
                start: "top bottom",
                end: "bottom center",
                scrub: 0.6,
              },
            },
          );
        });
      });
      gsap.fromTo(
        ".mc-story-heading h2 span",
        { opacity: 0.26 },
        {
          opacity: 1,
          stagger: 0.15,
          ease: "none",
          scrollTrigger: {
            trigger: root.current,
            start: "top 75%",
            end: "top 22%",
            scrub: 0.5,
          },
        },
      );
      return () => mm.revert();
    },
    { scope: root, dependencies: [reduced, ready], revertOnUpdate: true },
  );
  return (
    <section
      className="mc-story mc-wrap"
      ref={root}
      id="specialists"
      aria-labelledby="story-title"
    >
      <div className="mc-story-layout">
        <div className="mc-story-heading">
          <p className="mc-eyebrow">Connected through every shift</p>
          <h2 id="story-title">
            <span>Good care</span>
            <br />
            <span>is a team</span>
            <br />
            <span>effort.</span>
          </h2>
          <p>Give everyone the context to do their best work.</p>
          <a href="#assistant" className="mc-text-link">
            Meet your operations assistant <ArrowUpRight aria-hidden="true" />
          </a>
        </div>
        <div className="mc-story-cards">
          {chapters.map((chapter) => (
            <article className="mc-story-card" key={chapter.title}>
              <div className="mc-story-photo">
                <Image
                  src={`/landing/stills/${chapter.image}`}
                  alt={chapter.alt}
                  fill
                  sizes="(max-width: 767px) calc(100vw - 54px), (max-width: 1100px) 50vw, (max-width: 1392px) 49vw, 626px"
                />
              </div>
              <div className="mc-story-card-copy">
                <div className="mc-story-label">
                  <chapter.icon size={20} weight="light" aria-hidden="true" />
                  {chapter.label}
                </div>
                <h3>{chapter.title}</h3>
                <p>{chapter.copy}</p>
                <a
                  href={chapter.link}
                  className="mc-story-arrow"
                  aria-label={`Explore ${chapter.label.toLowerCase()}`}
                >
                  <ArrowUpRight size={22} aria-hidden="true" />
                </a>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
