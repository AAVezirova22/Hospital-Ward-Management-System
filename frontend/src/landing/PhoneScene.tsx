"use client";

import Image from "next/image";
import {
  ChatCircle,
  Check,
  FileCsv,
  WifiHigh,
} from "@phosphor-icons/react";
import {
  AnimatePresence,
  motion,
  type MotionValue,
  useTransform,
} from "motion/react";
import { useSiteMotion } from "./SiteMotion";

type Conversation = {
  request: string;
  response: string;
  followup: string;
};

export function PhoneScene({
  conversation,
  showFile,
  scrollProgress,
}: {
  conversation: Conversation;
  showFile: boolean;
  scrollProgress: MotionValue<number>;
}) {
  const { reduced } = useSiteMotion();

  const rotateY = useTransform(
    scrollProgress,
    [0.05, 0.32, 0.72, 0.96],
    [-72, 0, 0, 13],
  );
  const rotateX = useTransform(
    scrollProgress,
    [0.05, 0.35, 0.96],
    [8, 0, -4],
  );
  const rotateZ = useTransform(
    scrollProgress,
    [0.05, 0.35, 0.96],
    [-4, 0, 2.5],
  );
  const phoneScale = useTransform(
    scrollProgress,
    [0.05, 0.36, 0.72, 0.96],
    [0.82, 1.06, 1.06, 0.88],
  );
  const phoneX = useTransform(
    scrollProgress,
    [0.05, 0.68, 0.96],
    ["8vw", "0vw", "-17vw"],
  );
  const phoneY = useTransform(
    scrollProgress,
    [0.05, 0.36, 0.96],
    ["3vh", "0vh", "-1vh"],
  );
  const workspaceOpacity = useTransform(
    scrollProgress,
    [0.58, 0.76, 0.96],
    [0, 0.72, 1],
  );
  const workspaceScale = useTransform(
    scrollProgress,
    [0.58, 0.84, 0.96],
    [0.86, 0.96, 1],
  );
  const workspaceX = useTransform(
    scrollProgress,
    [0.58, 0.96],
    ["10vw", "12vw"],
  );
  const requestOpacity = useTransform(
    scrollProgress,
    [0.22, 0.3],
    [0, 1],
  );
  const requestY = useTransform(scrollProgress, [0.22, 0.3], [14, 0]);
  const fileOpacity = useTransform(scrollProgress, [0.31, 0.39], [0, 1]);
  const fileY = useTransform(scrollProgress, [0.31, 0.39], [12, 0]);
  const responseOpacity = useTransform(
    scrollProgress,
    [0.39, 0.48],
    [0, 1],
  );
  const responseY = useTransform(scrollProgress, [0.39, 0.48], [14, 0]);
  const followOpacity = useTransform(
    scrollProgress,
    [0.49, 0.57],
    [0, 1],
  );
  const followY = useTransform(scrollProgress, [0.49, 0.57], [14, 0]);
  const handoffOpacity = useTransform(
    scrollProgress,
    [0.7, 0.82],
    [0, 1],
  );
  const auraScale = useTransform(scrollProgress, [0.08, 0.52], [0.72, 1]);
  const auraOpacity = useTransform(scrollProgress, [0.08, 0.88], [0.25, 0.8]);

  return (
    <div className="mc-phone-stage">
      <motion.div
        className="mc-phone-aura"
        aria-hidden="true"
        style={{
          scale: reduced ? 1 : auraScale,
          opacity: reduced ? 0.58 : auraOpacity,
        }}
      />

      <div className="mc-phone-workspace-anchor" aria-hidden="true">
        <motion.div
          className="mc-phone-workspace"
          style={{
            opacity: reduced ? 1 : workspaceOpacity,
            scale: reduced ? 0.94 : workspaceScale,
            x: reduced ? 0 : workspaceX,
          }}
        >
          <div className="mc-phone-workspace-window">
            <header>
              <span>Medcore workspace</span>
              <span>Review before confirming</span>
            </header>
            <Image
              src="/landing/stills/workspace.webp"
              alt=""
              width={1440}
              height={1593}
              sizes="(max-width: 767px) 94vw, 48vw"
            />
          </div>
        </motion.div>
      </div>

      <div className="mc-phone-anchor">
        <motion.div
          className="mc-phone-device"
          data-testid="cinematic-phone"
          style={{
            rotateY: reduced ? 0 : rotateY,
            rotateX: reduced ? 0 : rotateX,
            rotateZ: reduced ? 0 : rotateZ,
            scale: reduced ? 1 : phoneScale,
            x: reduced ? 0 : phoneX,
            y: reduced ? 0 : phoneY,
          }}
        >
          <span className="mc-phone-side is-left-top" aria-hidden="true" />
          <span className="mc-phone-side is-left-bottom" aria-hidden="true" />
          <span className="mc-phone-side is-right" aria-hidden="true" />
          <div className="mc-phone-shell">
            <div className="mc-phone-face">
              <div className="mc-phone-island" aria-hidden="true" />
              <div className="mc-phone-status" aria-hidden="true">
                <span>9:41</span>
                <span className="mc-phone-status-meta">
                  <WifiHigh size={11} weight="bold" />
                  <span>5G</span>
                  <span className="mc-phone-battery" />
                </span>
              </div>

              <div className="mc-conversation mc-phone-conversation">
                <header>
                  <span className="mc-chat-avatar">
                    <ChatCircle size={20} weight="light" />
                  </span>
                  <div>
                    Medcore<small>In iMessage</small>
                  </div>
                  <span className="mc-example-label">Example</span>
                </header>

                <AnimatePresence mode="wait">
                  <motion.div
                    className="mc-message-thread"
                    key={`${conversation.request}-${showFile}`}
                    initial={reduced ? false : { opacity: 0.7 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0.7 }}
                    transition={{ duration: reduced ? 0 : 0.2 }}
                  >
                    <motion.p
                      className="mc-bubble is-sent"
                      style={{
                        opacity: reduced ? 1 : requestOpacity,
                        y: reduced ? 0 : requestY,
                      }}
                    >
                      {conversation.request}
                    </motion.p>
                    {showFile ? (
                      <motion.p
                        className="mc-message-file"
                        style={{
                          opacity: reduced ? 1 : fileOpacity,
                          y: reduced ? 0 : fileY,
                        }}
                      >
                        <FileCsv size={22} />
                        sample-admissions.csv
                      </motion.p>
                    ) : null}
                    <motion.p
                      className="mc-bubble is-received"
                      style={{
                        opacity: reduced ? 1 : responseOpacity,
                        y: reduced ? 0 : responseY,
                      }}
                    >
                      {conversation.response}
                    </motion.p>
                    <motion.p
                      className="mc-bubble is-sent"
                      style={{
                        opacity: reduced ? 1 : followOpacity,
                        y: reduced ? 0 : followY,
                      }}
                    >
                      {conversation.followup}
                    </motion.p>
                    <span className="mc-message-read">
                      <Check size={12} /> Illustrative conversation
                    </span>
                  </motion.div>
                </AnimatePresence>
              </div>
            </div>
          </div>
        </motion.div>
      </div>

      <motion.div
        className="mc-phone-handoff"
        aria-hidden="true"
        style={{ opacity: reduced ? 1 : handoffOpacity }}
      >
        <span>Message</span>
        <i />
        <span>Review in Medcore</span>
      </motion.div>
    </div>
  );
}
