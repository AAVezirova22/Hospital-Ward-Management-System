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
          // Opening shot: the frame widens from a letterboxed aperture while
          // the headline rises out of its line masks.
          const opening = gsap.timeline({ defaults: { ease: "power3.out" } });
          opening
            .fromTo(
              ".mc-hero-frame",
              {
                clipPath: desktop
                  ? "inset(11% 9% 11% 9% round 40px)"
                  : "inset(6% 4% 6% 4% round 26px)",
              },
              {
                clipPath: "inset(0% 0% 0% 0% round 26px)",
                duration: 1.6,
                ease: "expo.inOut",
                clearProps: "clipPath",
              },
            )
            .from(
              ".mc-hero .mc-eyebrow",
              { y: 14, opacity: 0, duration: 0.8 },
              "-=0.75",
            )
            .from(
              ".mc-hero h1 .mc-line > span",
              { yPercent: 112, duration: 1.2, stagger: 0.14 },
              "<0.1",
            )
            .from(
              ".mc-hero-lede, .mc-hero .mc-actions",
              { y: 20, opacity: 0, duration: 0.9, stagger: 0.12 },
              "-=0.75",
            )
            .from(".mc-hero-note", { y: 36, opacity: 0, duration: 1 }, "-=0.7");

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
          // Dolly-out: the frame recedes while the photograph drifts and
          // pushes in, so leaving the hero reads as a camera move, not a cut.
          // The image wrapper belongs to Motion; GSAP moves the img inside it.
          const leaving = {
            trigger: ".mc-hero",
            start: "top top",
            end: "bottom top",
            scrub: 0.8,
          };
          gsap.to(".mc-hero-frame", {
            scale: desktop ? 0.94 : 0.97,
            borderRadius: desktop ? 44 : 32,
            ease: "none",
            scrollTrigger: leaving,
          });
          gsap.fromTo(
            ".mc-hero-image img",
            { yPercent: 0, scale: 1 },
            { yPercent: 8, scale: 1.16, ease: "none", scrollTrigger: leaving },
          );
          // The "thousand moving parts" line assembles as the reader arrives.
          gsap.fromTo(
            ".mc-intro .mc-word",
            { opacity: 0.12, yPercent: 22 },
            {
              opacity: 1,
              yPercent: 0,
              stagger: 0.08,
              ease: "none",
              scrollTrigger: {
                trigger: ".mc-intro h2",
                start: "top 85%",
                end: "bottom 45%",
                scrub: 0.6,
              },
            },
          );
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
          // The product chapter rises like a stage, widening to full bleed.
          gsap.fromTo(
            ".mc-platform",
            {
              clipPath: desktop
                ? "inset(0% 5% 0% 5% round 64px 64px 0px 0px)"
                : "inset(0% 3% 0% 3% round 32px 32px 0px 0px)",
            },
            {
              clipPath: "inset(0% 0% 0% 0% round 40px 40px 0px 0px)",
              ease: "none",
              scrollTrigger: {
                trigger: ".mc-platform",
                start: "top bottom",
                end: "top 15%",
                scrub: 0.8,
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
          // Closing shot mirrors the opening: the panel opens from an
          // aperture and its headline rises out of the same line masks.
          gsap.fromTo(
            ".mc-close",
            {
              clipPath: desktop
                ? "inset(7% 6% 7% 6% round 64px)"
                : "inset(3% 3% 3% 3% round 32px)",
            },
            {
              clipPath: "inset(0% 0% 0% 0% round 32px)",
              ease: "none",
              scrollTrigger: {
                trigger: ".mc-close",
                start: "top bottom",
                end: "top 30%",
                scrub: 0.8,
              },
            },
          );
          gsap.from(".mc-close h2 .mc-line > span", {
            yPercent: 112,
            duration: 1.15,
            stagger: 0.14,
            ease: "power3.out",
            scrollTrigger: { trigger: ".mc-close", start: "top 65%" },
          });
        },
      );
      return () => media.revert();
    },
    { scope: root, dependencies: [active], revertOnUpdate: true },
  );
  return null;
}
