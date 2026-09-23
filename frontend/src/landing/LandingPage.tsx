import Image from "next/image";
import Link from "next/link";
import {
  ArrowUpRight,
  Fingerprint,
  Heartbeat,
  Files,
  ChartLineUp,
  ChatCircle,
} from "@phosphor-icons/react/dist/ssr";
import { LandingExperience, Reveal } from "./SiteMotion";
import { SiteNav, Walkthrough } from "./SiteChrome";
import { PresenceHero } from "./PresenceHero";
import { PlatformTour } from "./PlatformTour";
import { WorkflowStory } from "./WorkflowStory";
import { FileWorkflow } from "./FileWorkflow";
import { MessageExperience } from "./MessageExperience";

export function LandingPage({ nonce }: { nonce?: string }) {
  return (
    <LandingExperience nonce={nonce}>
      <a className="mc-skip" href="#main-content">
        Skip to content
      </a>
      <SiteNav />
      <main id="main-content">
        <PresenceHero />
        <section className="mc-intro mc-wrap" aria-labelledby="intro-title">
          <Reveal>
            <p className="mc-eyebrow">Made for the people behind the care</p>
            <h2 id="intro-title">
              A hospital is a thousand
              <br className="mc-desktop" /> moving parts.
              <span className="mc-inline-photo">
                <Image
                  src="/landing/stills/clinical-conversation.webp"
                  alt="A doctor talking with a patient at her bedside"
                  fill
                  sizes="(max-width: 767px) 85px, 128px"
                />
              </span>
              <br />
              Bring them together.
            </h2>
            <p className="mc-intro-copy">
              Your people, your wards, your next decision. Medcore connects the
              work around care, so you can be more present for it.
            </p>
          </Reveal>
          <div className="mc-capabilities" aria-label="Platform capabilities">
            <a href="#specialists">
              <Heartbeat weight="light" />A connected ward
            </a>
            <a href="#assistant">
              <Files weight="light" />
              Files into workflows
            </a>
            <a href="#workflow">
              <ChatCircle weight="light" />
              Assistant workflows
            </a>
            <a href="#platform">
              <ChartLineUp weight="light" />A clearer picture
            </a>
          </div>
        </section>
        <PlatformTour />
        <WorkflowStory />
        <FileWorkflow />
        <MessageExperience />
        <section
          className="mc-principles mc-wrap"
          id="technology"
          aria-labelledby="principles-title"
        >
          <Reveal className="mc-principles-heading">
            <Fingerprint size={42} weight="light" />
            <h2 id="principles-title">
              Thoughtful by design.
              <br />
              Human by default.
            </h2>
            <p>
              Better tools should give your team more control. Every part of
              Medcore is built around that idea.
            </p>
          </Reveal>
          <div className="mc-principles-grid">
            <Reveal>
              <h3>The right view for every role.</h3>
              <p>
                Administrators manage the hospital. Staff coordinate the ward.
                Patients see their own care history.
              </p>
              <Link className="mc-text-link" href="/app">
                Find your workspace <ArrowUpRight aria-hidden="true" />
              </Link>
            </Reveal>
            <Reveal delay={0.08}>
              <h3>Your team has the final word.</h3>
              <p>
                Review proposed changes before they happen. Confirmations
                expire, permissions are checked, and actions leave an audit
                trail.
              </p>
              <a className="mc-text-link" href="#assistant">
                Explore assisted workflows <ArrowUpRight aria-hidden="true" />
              </a>
            </Reveal>
          </div>
        </section>
        <section className="mc-close" id="start" aria-labelledby="start-title">
          <div className="mc-wrap mc-close-inner">
            <Reveal>
              <p className="mc-eyebrow">
                A little less admin. A lot more possibility.
              </p>
              <h2 id="start-title">
                Make room
                <br />
                for better care.
              </h2>
              <div className="mc-actions">
                <Link href="/app" className="mc-button">
                  Open the workspace{" "}
                  <span>
                    <ArrowUpRight aria-hidden="true" />
                  </span>
                </Link>
                <a href="#booking" className="mc-text-link" data-film-book>
                  Take a guided tour <ArrowUpRight aria-hidden="true" />
                </a>
              </div>
            </Reveal>
            <div className="mc-close-photo">
              <Image
                src="/landing/stills/hospital-lobby.webp"
                alt="Glass doors and reflected trees at a hospital entrance"
                fill
                sizes="(max-width: 767px) calc(100vw - 64px), (max-width: 1100px) calc((100vw - 148px) / 2.2), (max-width: 1436px) calc((100vw - 196px) / 2.2), 564px"
              />
            </div>
          </div>
        </section>
      </main>
      <footer className="mc-footer mc-wrap">
        <div className="mc-footer-top">
          <a href="#product" className="mc-brand">
            Medcore<span aria-hidden="true">®</span>
          </a>
          <p>More presence. Less process.</p>
          <a href="#product" className="mc-text-link">
            Back to the top <ArrowUpRight aria-hidden="true" />
          </a>
        </div>
        <div className="mc-footer-bottom">
          <span>© {new Date().getFullYear()} Medcore</span>
          <nav aria-label="Footer navigation">
            <a href="#platform">Product</a>
            <a href="#assistant">Assistant</a>
            <a href="#workflow">Assistant workflows</a>
            <Link href="/app">Sign in</Link>
            <a
              href="https://github.com/AAVezirova22/Hospital-Ward-Management-System"
              target="_blank"
              rel="noreferrer"
            >
              Project information <ArrowUpRight size={12} aria-hidden="true" />
            </a>
          </nav>
        </div>
      </footer>
      <Walkthrough />
    </LandingExperience>
  );
}
