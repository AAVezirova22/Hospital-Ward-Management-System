"use client";

import type { RefObject } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useGSAP } from "@gsap/react";

gsap.registerPlugin(useGSAP, ScrollTrigger);

/** Scroll moves the camera and chapter frames; Motion owns their contents. */
export function FilmChoreography({
  root,
  active,
}: {
  root: RefObject<HTMLDivElement | null>;
  active: boolean;
}) {
  useGSAP(
    () => {
      if (!active) return;
      const media = gsap.matchMedia();
      media.add(
        { desktop: "(min-width: 901px)", mobile: "(max-width: 900px)" },
        (context) => {
          const desktop = context.conditions?.desktop;
          const opening = gsap.timeline({ defaults: { ease: "power3.out" } });
          opening
            .from(".mc-hero h1 span", {
              yPercent: 65,
              opacity: 0,
              duration: 1.25,
              stagger: 0.16,
            })
            .from(
              ".mc-hero-lede, .mc-hero .mc-actions",
              { y: 20, opacity: 0, duration: 0.9, stagger: 0.12 },
              "-=0.75",
            );

          gsap.to(".mc-hero-copy", {
            y: desktop ? -65 : -24,
            opacity: 0.3,
            ease: "none",
            scrollTrigger: {
              trigger: ".mc-hero",
              start: "top top",
              end: "bottom 20%",
              scrub: 0.7,
            },
          });
          gsap.fromTo(
            ".mc-inline-photo",
            { scale: 0.8, rotation: -8 },
            {
              scale: 1,
              rotation: 0,
              ease: "power2.out",
              scrollTrigger: {
                trigger: ".mc-intro",
                start: "top 85%",
                end: "top 35%",
                scrub: 0.6,
              },
            },
          );
          gsap.fromTo(
            ".mc-product-bezel",
            {
              y: desktop ? 100 : 40,
              scale: desktop ? 0.84 : 0.96,
              rotationX: desktop ? 12 : 0,
              transformPerspective: 1600,
            },
            {
              y: 0,
              scale: 1,
              rotationX: 0,
              ease: "power2.out",
              scrollTrigger: {
                trigger: ".mc-platform-panel",
                start: "top 95%",
                end: "top 25%",
                scrub: 0.9,
              },
            },
          );
          gsap.utils.toArray<HTMLElement>(".mc-story-card").forEach((card) => {
            gsap.fromTo(
              card,
              { y: desktop ? 80 : 35, rotation: desktop ? 2 : 0, scale: 0.96 },
              {
                y: 0,
                rotation: 0,
                scale: 1,
                ease: "power2.out",
                scrollTrigger: {
                  trigger: card,
                  start: "top 95%",
                  end: "top 35%",
                  scrub: 0.65,
                },
              },
            );
          });
          gsap.fromTo(
            ".mc-workflow-input",
            { x: desktop ? -40 : 0, y: 32 },
            {
              x: 0,
              y: 0,
              ease: "power2.out",
              scrollTrigger: {
                trigger: ".mc-workflow-demo",
                start: "top 95%",
                end: "top 40%",
                scrub: 0.65,
              },
            },
          );
          gsap.fromTo(
            ".mc-workflow-result",
            { x: desktop ? 40 : 0, y: 48 },
            {
              x: 0,
              y: 0,
              ease: "power2.out",
              scrollTrigger: {
                trigger: ".mc-workflow-result",
                start: "top 95%",
                end: "top 40%",
                scrub: 0.65,
              },
            },
          );
          gsap.fromTo(
            ".mc-close-photo img",
            { scale: 1.16, yPercent: 3 },
            {
              scale: 1.04,
              yPercent: -1,
              ease: "none",
              scrollTrigger: {
                trigger: ".mc-close",
                start: "top bottom",
                end: "bottom bottom",
                scrub: 0.9,
              },
            },
          );
        },
      );
      return () => media.revert();
    },
    { scope: root, dependencies: [active], revertOnUpdate: true },
  );
  return null;
}
