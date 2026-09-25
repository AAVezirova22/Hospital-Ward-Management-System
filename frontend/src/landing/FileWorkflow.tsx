"use client";
import { useRef, useState } from "react";
import Link from "next/link";
import { AnimatePresence, motion } from "motion/react";
import {
  ArrowDown,
  ArrowUpRight,
  FileCsv,
  FileText,
  FolderOpen,
  Check,
  ArrowCounterClockwise,
  Sparkle,
  UploadSimple,
} from "@phosphor-icons/react";
import { WorkflowProposal } from "../features/assistant/WorkflowProposal";
import type { AiResponse } from "../ai-contract";
import { Reveal, useSiteMotion } from "./SiteMotion";

type Proposal = Extract<
  AiResponse,
  { responseType: "WORKFLOW_PROPOSAL" }
>["data"];
type Phase = "ready" | "review" | "confirmed" | "cancelled";
const sampleType = "application/medcore-sample";

export function FileWorkflow() {
  const [phase, setPhase] = useState<Phase>("ready");
  const [dragging, setDragging] = useState(false);
  const [notice, setNotice] = useState("");
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const { reduced } = useSiteMotion();
  const result = useRef<HTMLDivElement>(null);
  const prepare = () => {
    setNotice("");
    setDragging(false);
    setProposal({
      action: {
        id: 1,
        actionType: "WORKFLOW",
        expiresAt: new Date(Date.now() + 8 * 60 * 1000).toISOString(),
        status: "PENDING",
      },
      workflow: {
        title: "Prepare an admission",
        steps: [
          {
            key: "patient",
            operation: "createPatient",
            source: "sample-admissions.csv",
            evidence: {},
            fields: {
              firstName: "Vera",
              lastName: "Angelova",
              patientIdentifier: "DEMO-028",
            },
          },
          {
            key: "admission",
            operation: "admit",
            source: "sample-admissions.csv",
            evidence: {},
            fields: {
              patientId: "$patient",
              roomId: "307",
              doctorId: "Dr. Dimitrova",
            },
          },
        ],
      },
    });
    setPhase("review");
  };
  const reset = () => {
    setPhase("ready");
    setProposal(null);
    setNotice("");
  };
  return (
    <section
      className="mc-assistant"
      id="assistant"
      aria-labelledby="assistant-title"
    >
      <div className="mc-wrap">
        <Reveal className="mc-assistant-heading">
          <p className="mc-eyebrow">Your operations assistant</p>
          <h2 id="assistant-title">
            Files in.
            <br />
            <span className="mc-muted">A plan out.</span>
          </h2>
          <p>
            Drop in your files. Describe what needs to happen.
            <br className="mc-desktop" /> Review a complete workflow before
            anything changes.
          </p>
        </Reveal>
        <div className="mc-workflow-demo">
          <div className="mc-workflow-input">
            <div className="mc-sample-files">
              <div className="mc-file-back" aria-hidden="true">
                <FileText size={28} weight="light" />
                <span>ward-notes.pdf</span>
              </div>
              <button
                type="button"
                className="mc-file-front"
                draggable
                aria-label="Use sample admissions file"
                onClick={prepare}
                onDragStart={(event) => {
                  event.dataTransfer.setData(sampleType, "sample-admissions");
                  event.dataTransfer.effectAllowed = "copy";
                }}
              >
                <FileCsv size={36} weight="light" />
                <span>
                  sample-admissions.csv
                  <small>Patient, room, attending doctor</small>
                </span>
                <span className="mc-file-grip" aria-hidden="true">
                  ⠿
                </span>
              </button>
            </div>
            <ArrowDown
              className="mc-drop-arrow"
              size={24}
              weight="light"
              aria-hidden="true"
            />
            <div
              className={`mc-dropzone ${dragging ? "is-dragging" : ""}`}
              onDragOver={(event) => {
                event.preventDefault();
                setDragging(true);
              }}
              onDragLeave={(event) => {
                if (!event.currentTarget.contains(event.relatedTarget as Node))
                  setDragging(false);
              }}
              onDrop={(event) => {
                event.preventDefault();
                setDragging(false);
                if (
                  event.dataTransfer.getData(sampleType) === "sample-admissions"
                )
                  prepare();
                else
                  setNotice(
                    "To process your own files, open the secure workspace and sign in. This tour uses sample data.",
                  );
              }}
            >
              <UploadSimple size={30} weight="light" aria-hidden="true" />
              <h3>Drop the sample file here.</h3>
              <p>Or take the example for a spin.</p>
              <button type="button" className="mc-button" onClick={prepare}>
                Use sample files
                <span>
                  <ArrowUpRight size={18} aria-hidden="true" />
                </span>
              </button>
            </div>
            <div className="mc-file-types">
              <span>Spreadsheets</span>
              <span>Documents</span>
              <span>PDFs</span>
              <span>Folders</span>
            </div>
            {notice && (
              <p className="mc-demo-notice" role="status">
                {notice}{" "}
                <Link href="/app">
                  Open workspace <ArrowUpRight size={14} />
                </Link>
              </p>
            )}
            <p className="mc-demo-disclosure">
              Interactive example with sample data. Your files are processed
              inside your signed-in workspace.
            </p>
          </div>
          <div className="mc-workflow-result" ref={result}>
            <header className="mc-assistant-bar">
              <span className="mc-assistant-symbol">
                <Sparkle size={21} weight="light" aria-hidden="true" />
              </span>
              <div>
                Medcore assistant<small>From intention to action</small>
              </div>
              <span className="mc-example-label">Example</span>
            </header>
            <div
              className="mc-workflow-progress"
              aria-label="Workflow progress"
            >
              <span className="is-complete">
                <Check size={12} />
                Add files
              </span>
              <span className={phase !== "ready" ? "is-complete" : ""}>
                Review plan
              </span>
              <span className={phase === "confirmed" ? "is-complete" : ""}>
                Confirm
              </span>
            </div>
            <AnimatePresence mode="wait">
              <motion.div
                className="mc-workflow-state"
                key={phase}
                initial={reduced ? false : { opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: reduced ? 0 : 0.25 }}
              >
                {phase === "ready" && (
                  <div className="mc-workflow-empty">
                    <FolderOpen size={48} weight="light" aria-hidden="true" />
                    <h3>
                      A little context.
                      <br />A useful next step.
                    </h3>
                    <p>
                      Try the sample to see a file become a proposed admission,
                      ready for your review.
                    </p>
                    <div className="mc-example-request">
                      “Prepare the admission from this file.”
                    </div>
                  </div>
                )}
                {phase === "review" && proposal && (
                  <div className="mc-real-proposal">
                    <p className="mc-proposal-intro">
                      Here is the proposed workflow from the sample file. You
                      decide what happens next.
                    </p>
                    <WorkflowProposal
                      data={proposal}
                      busy={false}
                      onAction={(operation) =>
                        setPhase(
                          operation === "confirm" ? "confirmed" : "cancelled",
                        )
                      }
                    />
                  </div>
                )}
                {phase === "confirmed" && (
                  <div className="mc-workflow-success" role="status">
                    <span className="mc-success-ring">
                      <Check size={38} weight="light" />
                    </span>
                    <h3>
                      Reviewed by you.
                      <br />
                      Ready for the ward.
                    </h3>
                    <p>
                      You completed the example. In your workspace, confirmation
                      applies the reviewed steps together and records the
                      action.
                    </p>
                    <p className="mc-demo-disclosure">
                      No hospital records were created in this example.
                    </p>
                    <button className="mc-text-link" onClick={reset}>
                      <ArrowCounterClockwise size={16} />
                      Try it again
                    </button>
                  </div>
                )}
                {phase === "cancelled" && (
                  <div className="mc-workflow-success" role="status">
                    <h3>Always your decision.</h3>
                    <p>
                      The example proposal has been cancelled. Nothing has
                      changed.
                    </p>
                    <button className="mc-text-link" onClick={reset}>
                      <ArrowCounterClockwise size={16} />
                      Start again
                    </button>
                  </div>
                )}
              </motion.div>
            </AnimatePresence>
          </div>
        </div>
        <div className="mc-assistant-bottom">
          <p>
            From one admission to a complete department setup.
            <br />
            Connected files give your assistant the context to help.
          </p>
          <Link href="/app" className="mc-text-link">
            Put your assistant to work <ArrowUpRight aria-hidden="true" />
          </Link>
        </div>
      </div>
    </section>
  );
}
