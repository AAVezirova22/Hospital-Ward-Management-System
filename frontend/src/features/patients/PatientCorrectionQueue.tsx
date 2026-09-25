"use client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../../api";
import type { User } from "../../api";

type Correction = {
  id: number; patientId: number; patientIdentifier: string;
  currentFirstName: string; currentLastName: string;
  firstName?: string; lastName?: string; dateOfBirth?: string;
  address?: string; phoneNumber?: string; createdAt: string; requestedBy: string;
};

export function PatientCorrectionQueue({ user }: { user: User }) {
  const client = useQueryClient();
  const [notes, setNotes] = useState<Record<number, string>>({});
  const queue = useQuery({ queryKey: ["patient-correction-requests"], queryFn: () => api<Correction[]>("/patient-correction-requests"), enabled: user.role === "ADMIN" || user.role === "MEDICAL_STAFF" });
  const decision = useMutation({
    mutationFn: ({ id, decision }: { id: number; decision: "APPROVE" | "REJECT" }) => api(`/patient-correction-requests/${id}/decision`, "POST", { decision, note: notes[id] || null }),
    onSuccess: (_result, { id }) => { setNotes((current) => { const next = { ...current }; delete next[id]; return next; }); return client.invalidateQueries({ queryKey: ["patient-correction-requests"] }); },
  });
  if (user.role !== "ADMIN" && user.role !== "MEDICAL_STAFF") return null;
  return <section className="panel">
    <h2>Profile corrections awaiting review</h2>
    {queue.error && <p className="error" role="alert">{queue.error.message}</p>}
    {queue.data?.length ? queue.data.map((item) => <article className="result-row" key={item.id}>
      <span><strong>{item.currentFirstName} {item.currentLastName}</strong> · {item.patientIdentifier}<small>Requested by {item.requestedBy} · {new Date(item.createdAt).toLocaleString()}</small>
        <small>{Object.entries({ firstName: item.firstName, lastName: item.lastName, dateOfBirth: item.dateOfBirth, address: item.address, phoneNumber: item.phoneNumber }).filter(([, value]) => value).map(([field, value]) => `${field}: ${value}`).join(" · ")}</small>
      </span>
      <div className="actions"><button className="primary" disabled={decision.isPending} onClick={() => decision.mutate({ id: item.id, decision: "APPROVE" })}>Approve</button><button className="secondary" disabled={decision.isPending} onClick={() => decision.mutate({ id: item.id, decision: "REJECT" })}>Reject</button></div>
      <label>Review note for {item.currentFirstName} {item.currentLastName} (optional)<input maxLength={300} value={notes[item.id] || ""} onChange={(event) => setNotes((current) => ({ ...current, [item.id]: event.target.value }))} placeholder="Add a note for the patient" /></label>
    </article>) : <p>{queue.isLoading ? "Loading requests…" : "No pending requests."}</p>}
    {decision.error && <p className="error" role="alert">{decision.error.message}</p>}
  </section>;
}
