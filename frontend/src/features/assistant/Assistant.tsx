"use client";
import React, { useState, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { usePathname } from "next/navigation";
import {
  api,
  activeDepartment,
  setActiveDepartment,
  type Row,
} from "../../api";
import { ErrorBox, Modal } from "../../components/workspace";
import { Sparkles, X, ArrowUpRight, ArrowRight, Activity } from "../../icons";
import { aiResponse, type AiResponse } from "../../ai-contract";
import { AssistantSources, useAssistantSources } from "./AssistantSources";
import { WorkflowProposal } from "./WorkflowProposal";
import { AssistantTurn } from "./AssistantResults";

/** The confirmed shape of a workflow proposal turn, as validated by the response contract. */
type WorkflowProposalData = Extract<
  AiResponse,
  { responseType: "WORKFLOW_PROPOSAL" }
>["data"];
export function Assistant({ onClose }: { onClose: () => void }) {
  const pathname = usePathname(),
    client = useQueryClient();
  const [message, setMessage] = useState(""),
    [session, setSession] = useState<string | null>(null),
    [results, setResults] = useState<Row[]>([]),
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
        <span className="eyebrow">A shorter path to the answer</span>
        <h2>What needs your attention?</h2>
        <p>
          Connect a folder or upload files, then describe the workflow you want.
          Review the proposed steps before applying changes.
        </p>
        <AssistantSources model={sources} busy={busy} run={fileAction} />
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
          <div key={i} ref={i === results.length - 1 ? lastResult : undefined}>
            {r.responseType === "WORKFLOW_PROPOSAL" ? (
              <WorkflowProposal
                data={r.data as WorkflowProposalData}
                done={r.done as string | undefined}
                busy={busy}
                onAction={(op) =>
                  action((r.data as WorkflowProposalData).action.id, op, i)
                }
              />
            ) : (
              <AssistantTurn
                r={r}
                i={i}
                busy={busy}
                onClose={onClose}
                onAction={action}
              />
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
