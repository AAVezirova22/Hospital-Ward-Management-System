import type { Metadata } from "next";
import { LandingPage } from "../src/landing/LandingPage";
import "../src/landing/landing.css";

export const metadata: Metadata = {
  title: "Medcore | The ward, in the moment.",
  description:
    "Hospital operations with an assistant that plans from files and Messages, then waits for a human to confirm.",
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
