"use client";

import { useState } from "react";
import Image from "next/image";
import { ChatCircle, Check, FileCsv, WifiHigh } from "@phosphor-icons/react";
import {
  AnimatePresence,
  motion,
  type MotionValue,
  useTransform,
} from "motion/react";
import { useSiteMotion } from "./SiteMotion";
import { AmbientLight } from "./AmbientLight";

type Conversation = {
  request: string;
  response: string;
  followup: string;
};

export function PhoneScene({
  conversation,
  showFile,
  progress,
}: {
  conversation: Conversation;
  showFile: boolean;
  progress: MotionValue<number>;
}) {
  const { reduced } = useSiteMotion();
  const [frontSurface, setFrontSurface] = useState<"phone" | "workspace">(
    "phone",
  );

  const rotateY = useTransform(progress, [0, 0.2, 0.64, 0.95], [-32, 0, 0, -9]);
  const rotateX = useTransform(progress, [0, 0.2, 0.95], [7, 0, -2]);
  const rotateZ = useTransform(progress, [0, 0.2, 0.95], [-5, 0, -2]);
  const phoneScale = useTransform(
    progress,
    [0, 0.2, 0.64, 0.95],
    [0.86, 1, 1, 0.88],
  );
  const phoneX = useTransform(progress, [0, 0.64, 0.95], ["0%", "0%", "-30%"]);
  const phoneY = useTransform(progress, [0, 0.2, 0.95], [42, 0, -14]);
  const workspaceOpacity = useTransform(progress, [0.66, 0.91], [0, 1]);
  const workspaceScale = useTransform(progress, [0.66, 0.95], [0.86, 1]);
  const workspacePointerEvents = useTransform(
    progress,
    [0.66, 0.76],
    (value) => (value >= 0.74 ? "auto" : "none"),
  );
  const workspaceX = useTransform(progress, [0.66, 0.95], ["10%", "0%"]);
  const requestOpacity = useTransform(progress, [0.18, 0.23], [0, 1]);
  const requestY = useTransform(progress, [0.18, 0.23], [8, 0]);
  const fileOpacity = useTransform(progress, [0.27, 0.32], [0, 1]);
  const fileY = useTransform(progress, [0.27, 0.32], [8, 0]);
  const responseOpacity = useTransform(progress, [0.37, 0.42], [0, 1]);
  const responseY = useTransform(progress, [0.37, 0.42], [8, 0]);
  const followOpacity = useTransform(progress, [0.54, 0.59], [0, 1]);
  const followY = useTransform(progress, [0.54, 0.59], [8, 0]);
  const handoffOpacity = useTransform(progress, [0.89, 0.96], [0, 1]);
  const auraScale = useTransform(progress, [0, 0.95], [0.96, 1.04]);
  const auraOpacity = useTransform(progress, [0, 0.2], [0.35, 0.55]);

  return (
    <div className="mc-phone-stage">
      <AmbientLight />
      <motion.div
        className="mc-phone-aura"
        aria-hidden="true"
        style={{
          scale: reduced ? 1 : auraScale,
          opacity: reduced ? 0.58 : auraOpacity,
        }}
      />

      <div
        className="mc-phone-workspace-anchor"
        data-front={frontSurface === "workspace"}
        onPointerEnter={() => setFrontSurface("workspace")}
        aria-hidden="true"
        style={{ zIndex: frontSurface === "workspace" ? 4 : 1 }}
      >
        <motion.div
          className="mc-phone-workspace"
          data-front={frontSurface === "workspace"}
          style={{
            opacity: reduced ? 1 : workspaceOpacity,
            scale: reduced ? 0.94 : workspaceScale,
            x: reduced ? 0 : workspaceX,
            pointerEvents: reduced ? "auto" : workspacePointerEvents,
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
              sizes="(max-width: 767px) calc(100vw - 72px), (max-width: 1100px) 45vw, 520px"
            />
          </div>
        </motion.div>
      </div>

      <div
        className="mc-phone-anchor"
        data-front={frontSurface === "phone"}
        onPointerEnter={() => setFrontSurface("phone")}
        style={{ zIndex: frontSurface === "phone" ? 4 : 1 }}
      >
        <motion.div
          className="mc-phone-device"
          data-testid="cinematic-phone"
          data-front={frontSurface === "phone"}
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
                    initial={reduced ? false : { opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0 }}
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
                    <motion.span
                      className="mc-message-read"
                      style={{ opacity: reduced ? 1 : followOpacity }}
                    >
                      <Check size={12} /> Illustrative conversation
                    </motion.span>
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
