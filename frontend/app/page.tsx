import type { Metadata } from "next";
import { headers } from "next/headers";
import { LandingPage } from "../src/landing/LandingPage";
import "../src/landing/presence.css";
import "../src/landing/phone-scene.css";

export const metadata: Metadata = {
  title: { absolute: "Medcore | More presence. Less process." },
  description:
    "One connected workspace for your ward, your team, and your workflows. Hospital operations with file-powered AI, iMessage, and human-confirmed changes.",
};

export default async function Home() {
  const nonce = (await headers()).get("x-nonce") ?? undefined;
  return (
    <>
      <noscript>
        <style
          nonce={nonce}
        >{`.mc [data-reveal], .mc h1 span, .mc-hero-copy > div { opacity: 1 !important; transform: none !important; }`}</style>
      </noscript>
      <LandingPage nonce={nonce} />
    </>
  );
}
