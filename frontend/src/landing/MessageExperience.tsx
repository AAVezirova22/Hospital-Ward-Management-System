"use client";
import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { AnimatePresence, motion } from "motion/react";
import {
  ArrowUpRight,
  ChatCircle,
  Check,
  FileCsv,
} from "@phosphor-icons/react";
import { Reveal, useSiteMotion } from "./SiteMotion";

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
  const { reduced } = useSiteMotion();
  const conversation = conversations[selected];
  return (
    <section
      className="mc-messages mc-wrap"
      id="workflow"
      aria-labelledby="messages-title"
    >
      <div className="mc-message-art">
        <div className="mc-message-photo">
          <Image
            src="/landing/stills/presence.webp"
            alt="Hospital professionals staying connected through their work"
            fill
            sizes="(max-width: 767px) 94vw, 48vw"
          />
        </div>
        <div className="mc-conversation">
          <header>
            <span className="mc-chat-avatar">
              <ChatCircle size={23} weight="light" />
            </span>
            <div>
              Medcore<small>In iMessage</small>
            </div>
            <span className="mc-example-label">Example</span>
          </header>
          <AnimatePresence mode="wait">
            <motion.div
              className="mc-message-thread"
              key={selected}
              initial={reduced ? false : { opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ duration: reduced ? 0 : 0.24 }}
            >
              <p className="mc-bubble is-sent">{conversation.request}</p>
              {selected === 1 && (
                <p className="mc-message-file">
                  <FileCsv size={24} />
                  sample-admissions.csv
                </p>
              )}
              <p className="mc-bubble is-received">{conversation.response}</p>
              <p className="mc-bubble is-sent">{conversation.followup}</p>
              <span className="mc-message-read">
                <Check size={12} /> Illustrative conversation
              </span>
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
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
          Between rounds. Away from your desk. Reach your Medcore assistant in
          iMessage and keep the work moving.
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
    </section>
  );
}
