import type { Metadata } from "next";
import { LandingPage } from "../src/landing/LandingPage";
import "../src/landing/landing.css";

export const metadata: Metadata = {
  title: "Medcore | Advanced care. Human at heart.",
  description:
    "Specialist care, modern diagnostics, and treatment built around you.",
};

export default function Home() {
  return (
    <>
      <link
        rel="preload"
        as="image"
        href="/landing/stills/hero.jpg"
        fetchPriority="high"
      />
      <LandingPage />
    </>
  );
}
