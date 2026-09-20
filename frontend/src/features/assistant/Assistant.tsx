"use client";
import React, { useState, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter, usePathname } from "next/navigation";
import {
  api,
  fullName,
  date,
  patientHref,
  activeDepartment,
  setActiveDepartment,
  type Row,
} from "../../api";
import { useUser, ErrorBox, Status, Modal } from "../../components/workspace";
import { Sparkles, X, ArrowUpRight, ArrowRight, Activity } from "lucide-react";
import { aiResponse, safeRoute, type AiResponse } from "../../ai-contract";
import { CommandResults } from "./CommandResults";
import { ProposalPreview } from "./ProposalPreview";
import { AssistantSources, useAssistantSources } from "./AssistantSources";
import { WorkflowProposal } from "./WorkflowProposal";
import { AiReport } from "./AssistantResults";
/** One rendered turn: the authorized response plus the question that produced it. */
type AiResult = Exclude<AiResponse, { responseType: "FILE_REQUEST" }> & {
  query: string;
  done?: string;
};

export function Assistant({ onClose }: { onClose: () => void }) {
  const user = useUser();
  const router = useRouter(),
    pathname = usePathname(),
    client = useQueryClient();
  const [message, setMessage] = useState(""),
    [session, setSession] = useState<string | null>(null),
    [results, setResults] = useState<AiResult[]>([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<Error | null>(null);
  const sources = useAssistantSources();
  const mounted = useRef(true);
  const lastResult = useRef<HTMLDivElement>(null);
  useEffect(() => {
    lastResult.current?.scrollIntoView({ block: "start" });
  }, [results.length]);
  useEffect(() => {
    mounted.current = true;
    let department = activeDepartment();
    const close = () => {
      const next = activeDepartment();
      if (department !== null && next !== department) onClose();
      department = next;
    };
    window.addEventListener("workspace-changed", close);
    return () => {
      mounted.current = false;
      window.removeEventListener("workspace-changed", close);
    };
  }, [onClose]);
  async function fileAction(action: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      if ((e as Error).name !== "AbortError") setError(e as Error);
    } finally {
      setBusy(false);
    }
  }
  async function send(text: string) {
    if (!text.trim() || busy) return;
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      let nextSession = session;
      for (let round = 0; round < 4; round++) {
        const raw = await api("/assistant/messages", "POST", {
          message: text,
          sessionId: nextSession,
          sourceIds: sources.sourceIds(),
          connectedFiles: sources.manifest(),
          route: pathname,
          selectedPatientId: /\/patients\/\d+$/.test(pathname)
            ? Number(pathname.split("/").pop())
            : null,
        });
        const result = aiResponse.parse(raw);
        if (!mounted.current) return;
        nextSession = result.sessionId;
        setSession(result.sessionId);
        if (result.responseType === "FILE_REQUEST") {
          if (round === 3)
            throw new Error(
              "The request needs too many file reads. Try a smaller folder or a more specific request.",
            );
          await sources.read(result.data.ids);
          continue;
        }
        setResults((r) => [...r, { query: text, ...result }]);
        break;
      }
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }
  async function action(id: number, op: string, index: number) {
    setBusy(true);
    setError(null);
    try {
      const completed = await api<{ departmentId?: number }>(
        `/ai-actions/${id}/${op}`,
        "POST",
      );
      setResults((r) =>
        r.map((v, i) =>
          i === index
            ? { ...v, done: op === "confirm" ? "EXECUTED" : "CANCELLED" }
            : v,
        ),
      );
      await client.invalidateQueries();
      if (op === "confirm" && completed?.departmentId)
        setActiveDepartment(completed.departmentId);
    } catch (e) {
      setError(e as Error);
      await client.invalidateQueries();
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title="Operations assistant" onClose={onClose}>
      <div className="assistant-head">
        <div>
          <Sparkles size={22} />
          <span>
            Operations assistant<small>MEDCORE / COMMAND</small>
          </span>
        </div>
        <button className="icon" aria-label="Close assistant" onClick={onClose}>
          <X />
        </button>
      </div>
      <div className="assistant-content">
        <span className="eyebrow">A SHORTER PATH TO THE ANSWER</span>
        <h2>What needs your attention?</h2>
        <p>
          Connect a folder or upload files, then describe the workflow you want.
          Review the proposed steps before applying changes.
        </p>
        <AssistantSources model={sources} busy={busy} run={fileAction} />
        <CommandResults query={message} onClose={onClose} user={user} />
        <div className="suggestions">
          {[
            "Show department status",
            "Rooms with two free beds",
            "Procedures today",
            "Find Petrov",
          ].map((q) => (
            <button key={q} onClick={() => send(q)} disabled={busy}>
              {q}
              <ArrowUpRight size={14} />
            </button>
          ))}
        </div>
        {results.map((r, i) => (
          <div
            className="ai-result"
            key={i}
            ref={i === results.length - 1 ? lastResult : undefined}
          >
            <div className="ai-query">› {r.query}</div>
            <p>{r.message}</p>
            <small className="ai-mode">
              {r.model === "local-command-model"
                ? "Local command mode"
                : "Configured model"}{" "}
              · Backend-authorized results
            </small>
            {r.responseType === "PATIENT_LIST" &&
              r.data.patients.map((p) => (
                <button
                  className="result-row"
                  key={p.id}
                  onClick={() => {
                    router.push(patientHref(p));
                    onClose();
                  }}
                >
                  <span>
                    {fullName(p)}
                    <small>{p.patientIdentifier}</small>
                  </span>
                  <ArrowUpRight size={16} />
                </button>
              ))}
            {r.responseType === "ROOM_LIST" &&
              r.data.rooms.map((room) => (
                <div className="result-row" key={room.id}>
                  <span>Room {room.roomNumber}</span>
                  <strong>{room.availableBeds} free</strong>
                </div>
              ))}
            {r.responseType === "PATIENT_SUMMARY" && (
              <>
                <h3>{fullName(r.data.patient)}</h3>
                <p>{r.data.admissions.length} recorded hospitalizations.</p>
                {r.data.admissions.map((v) => (
                  <div className="result-row" key={v.admission.id}>
                    <span>
                      {v.admission.admissionNumber}
                      <small>Dr. {fullName(v.doctor)}</small>
                    </span>
                    <Status value={v.admission.status} />
                  </div>
                ))}
                <button
                  className="secondary"
                  onClick={() => {
                    router.push(patientHref(r.data.patient));
                    onClose();
                  }}
                >
                  Open dossier
                </button>
              </>
            )}
            {r.responseType === "REPORT_RESULT" && <AiReport data={r.data} />}
            {r.responseType === "WORKFLOW_PROPOSAL" && (
              <WorkflowProposal
                data={r.data}
                done={r.done}
                busy={busy}
                onAction={(op) => action(r.data.action.id, op, i)}
              />
            )}
            {r.responseType === "NAVIGATION_COMMAND" && (
              <button
                className="primary"
                onClick={() => {
                  if (safeRoute.safeParse(r.data.route).success) {
                    router.push(r.data.route);
                    onClose();
                  }
                }}
              >
                Open view
                <ArrowRight size={16} />
              </button>
            )}
            {r.responseType === "CONFIRMATION_CARD" && (
              <div className="confirmation">
                <span className="eyebrow">
                  {r.data.action.actionType} PROPOSAL
                </span>
                <h3>{fullName(r.data.patient)}</h3>
                <ProposalPreview
                  current={r.data.current}
                  destination={r.data.destination}
                  actionType={r.data.action.actionType}
                  expiresAt={r.data.action.expiresAt}
                />
                {r.data.current && (
                  <p>
                    Current room:{" "}
                    {
                      r.data.current.rooms.find((x) => !x.assignment.releasedAt)
                        ?.room.roomNumber
                    }
                  </p>
                )}
                {r.data.destination && (
                  <p>Destination: Room {r.data.destination.roomNumber}</p>
                )}
                {r.data.doctor && <p>Doctor: {fullName(r.data.doctor)}</p>}
                <p>Expires {date(r.data.action.expiresAt)}</p>
                {r.done ? (
                  <Status value={r.done} />
                ) : (
                  <div className="actions">
                    <button
                      className="secondary"
                      disabled={busy}
                      onClick={() => action(r.data.action.id, "cancel", i)}
                    >
                      Cancel proposal
                    </button>
                    <button
                      className="primary"
                      disabled={
                        busy ||
                        Date.parse(r.data.action.expiresAt) <= Date.now()
                      }
                      onClick={() => action(r.data.action.id, "confirm", i)}
                    >
                      Confirm {r.data.action.actionType.toLowerCase()}
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
        {busy && (
          <div className="analyzing">
            <Activity size={18} />
            Checking operational state…
          </div>
        )}
        <ErrorBox error={error} />
      </div>
      <form
        className="assistant-input"
        onSubmit={(e) => {
          e.preventDefault();
          send(message);
        }}
      >
        <label className="sr-only" htmlFor="assistant-message">
          Assistant message
        </label>
        <input
          id="assistant-message"
          autoFocus
          placeholder="Ask about your department…"
          value={message}
          maxLength={2000}
          onChange={(e) => setMessage(e.target.value)}
        />
        <button
          className="primary"
          disabled={busy || !message.trim()}
          aria-label="Send message"
        >
          <ArrowRight size={18} />
        </button>
      </form>
      <div className="assistant-footer">
        <span>Operational support · Human-confirmed changes</span>
        <button
          className="text-button"
          disabled={busy}
          onClick={() =>
            fileAction(async () => {
              if (session)
                await api(`/assistant/sessions/${session}/clear`, "POST");
              await sources.clear();
              setSession(null);
              setResults([]);
            })
          }
        >
          Clear
        </button>
      </div>
    </Modal>
  );
}
