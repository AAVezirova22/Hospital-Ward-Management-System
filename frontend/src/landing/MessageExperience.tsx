"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import { ArrowUpRight, ChatCircle } from "@phosphor-icons/react";
import { useScroll } from "motion/react";
import { Reveal } from "./SiteMotion";
import { PhoneScene } from "./PhoneScene";

const conversations = [
  {
    label: "Find a room",
    request: "Which rooms have two free beds?",
    response:
      "In this example, room 307 has two available beds. Would you like to prepare an admission?",
    followup: "I'll review it in Medcore.",
  },
  {
    label: "Start a workflow",
    request: "Prepare the admissions from this file.",
    response:
      "The example file is ready for review. Open Medcore to check the patient details and confirm the proposed workflow.",
    followup: "Perfect. I'll take it from here.",
  },
  {
    label: "Check the ward",
    request: "Show department status.",
    response:
      "Example department: 14 active admissions, 8 rooms, and 3 doctors. Open the workspace for the complete ward view.",
    followup: "Just what I needed. Thank you.",
  },
];

export function MessageExperience() {
  const [selected, setSelected] = useState(0);
  const section = useRef<HTMLElement>(null);
  const conversation = conversations[selected];
  const { scrollYProgress } = useScroll({
    target: section,
    offset: ["start start", "end end"],
  });

  return (
    <section
      className="mc-messages mc-phone-story"
      id="workflow"
      aria-labelledby="messages-title"
      ref={section}
    >
      <div className="mc-phone-sticky">
        <div className="mc-wrap mc-phone-grid">
          <PhoneScene
            conversation={conversation}
            showFile={selected === 1}
            scrollProgress={scrollYProgress}
          />

          <Reveal className="mc-messages-copy">
            <ChatCircle
              className="mc-feature-icon"
              size={38}
              weight="light"
              aria-hidden="true"
            />
            <h2 id="messages-title">
              The conversation
              <br />
              carries on.
            </h2>
            <p>
              Between rounds. Away from your desk. Reach your Medcore assistant
              in iMessage and keep the work moving.
            </p>
            <div
              className="mc-message-options"
              aria-label="Choose an iMessage example"
            >
              {conversations.map((item, index) => (
                <button
                  key={item.label}
                  aria-pressed={selected === index}
                  onClick={() => setSelected(index)}
                >
                  {item.label}
                  <ArrowUpRight size={16} aria-hidden="true" />
                </button>
              ))}
            </div>
            <p className="mc-message-note">
              A familiar conversation. A connected workflow.
              <br />
              Your team stays in control.
            </p>
            <Link href="/app" className="mc-text-link">
              Continue in Medcore <ArrowUpRight aria-hidden="true" />
            </Link>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
