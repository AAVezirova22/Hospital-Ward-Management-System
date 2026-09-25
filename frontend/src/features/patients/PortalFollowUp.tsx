"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api";
import { ErrorBox } from "../../components/workspace";

type ConsentOption = {
  consentType: string;
  consentVersion: string;
  description: string;
};
type Consent = {
  id: number;
  consentType: string;
  consentVersion: string;
  recordedAt: string;
  withdrawnAt: string | null;
};
type Summary = {
  id: number;
  summary: string;
  approvedAt: string;
  reviewedAt: string;
};

export function PortalFollowUp() {
  const client = useQueryClient();
  const options = useQuery({
    queryKey: ["portal-consent-options"],
    queryFn: () => api<ConsentOption[]>("/portal/consent-options"),
  });
  const consents = useQuery({
    queryKey: ["portal-consents"],
    queryFn: () => api<Consent[]>("/portal/consents"),
  });
  const summaries = useQuery({
    queryKey: ["portal-care-summaries"],
    queryFn: () => api<Summary[]>("/portal/care-summaries"),
  });
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const summaryOption = options.data?.find(
    (option) => option.consentType === "PORTAL_FOLLOW_UP_SUMMARY",
  );
  const active = consents.data?.find(
    (consent) =>
      consent.consentType === summaryOption?.consentType &&
      !consent.withdrawnAt,
  );
  async function change(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      setConfirmed(false);
      await client.invalidateQueries({ queryKey: ["portal-consents"] });
      await client.invalidateQueries({ queryKey: ["portal-care-summaries"] });
    } catch (cause) {
      setError(cause as Error);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="portal-follow-up panel">
      <h2>Your follow-up plan</h2>
      <p>
        Only summaries approved by your clinician appear here. Internal staff
        tasks and notes stay private.
      </p>
      <ErrorBox
        error={options.error || consents.error || summaries.error || error}
      />
      {(options.isLoading || consents.isLoading || summaries.isLoading) && (
        <p>Loading your follow-up choices…</p>
      )}
      {summaryOption && (
        <div className="portal-consent">
          <h3>Follow-up summary choice</h3>
          <p>{summaryOption.description}</p>
          <small>Consent version {summaryOption.consentVersion}</small>
          {active ? (
            <>
              <p>
                Allowed since {new Date(active.recordedAt).toLocaleDateString()}
                .
              </p>
              <label>
                <input
                  type="checkbox"
                  checked={confirmed}
                  onChange={(event) => setConfirmed(event.target.checked)}
                />
                I want to withdraw this consent.
              </label>
              <button
                className="secondary"
                disabled={busy || !confirmed}
                onClick={() =>
                  void change(() =>
                    api(`/portal/consents/${active.id}/withdraw`, "POST", {
                      confirmed: true,
                    }),
                  )
                }
              >
                Withdraw consent
              </button>
            </>
          ) : (
            <>
              <p>
                You control whether approved summaries are shown in your portal.
              </p>
              <label>
                <input
                  type="checkbox"
                  checked={confirmed}
                  onChange={(event) => setConfirmed(event.target.checked)}
                />
                I agree to see clinician-approved follow-up summaries in this
                portal.
              </label>
              <button
                className="primary"
                disabled={busy || !confirmed}
                onClick={() =>
                  void change(() =>
                    api("/portal/consents", "POST", {
                      consentType: summaryOption.consentType,
                      consentVersion: summaryOption.consentVersion,
                      confirmed: true,
                    }),
                  )
                }
              >
                Allow follow-up summaries
              </button>
            </>
          )}
        </div>
      )}
      {active && (
        <>
          {summaries.data?.length ? (
            summaries.data.map((item) => (
              <article className="portal-summary" key={item.id}>
                <small>
                  Approved {new Date(item.approvedAt).toLocaleDateString()}
                </small>
                <p>{item.summary}</p>
              </article>
            ))
          ) : (
            <p>No clinician-approved follow-up summary is available yet.</p>
          )}
        </>
      )}
    </section>
  );
}
