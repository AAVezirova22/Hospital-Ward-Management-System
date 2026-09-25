"use client";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Row } from "../../api";
import { ErrorBox, Empty, Title, useData } from "../../components/workspace";

type Pending = { userId: number; username: string; email: string; registeredFirstName: string; registeredLastName: string; registeredDateOfBirth: string; temporaryPatientIdentifier: string };
type PatientPage = { items: Row[] };

export function PatientAccountReview() {
  const client = useQueryClient();
  const { data, error, isLoading } = useData("/patient-account-links");
  const [selection, setSelection] = useState<Record<number, string>>({});
  const [evidence, setEvidence] = useState<Record<number, string>>({});
  const [search, setSearch] = useState("");
  const patients = useQuery({
    queryKey: ["patient-link-candidates", search],
    queryFn: () => api<PatientPage>(`/patients?q=${encodeURIComponent(search)}&page=0&size=30`),
    enabled: search.trim().length > 1,
  });
  const link = useMutation({
    mutationFn: ({ userId, patientId, evidence }: { userId: number; patientId: number; evidence: string }) =>
      api(`/patient-account-links/${userId}`, "POST", { patientId, evidence }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["/patient-account-links"] });
      await client.invalidateQueries({ queryKey: ["/patients"] });
    },
  });
  const accounts = (data ?? []) as Pending[];
  return <>
    <Title eyebrow="Patient access" title="Review portal account links" description="Compare each verified registration with the hospital record. Record the evidence you checked before linking." />
    <ErrorBox error={error || link.error} />
    {isLoading ? <p>Loading verified accounts…</p> : accounts.length === 0 ? <Empty text="No verified portal accounts are waiting for review." /> : <div className="stack">
      {accounts.map((account) => <section className="panel" key={account.userId}>
        <h2>{account.registeredFirstName} {account.registeredLastName}</h2>
        <p><strong>{account.username}</strong> · {account.email} · Email verified</p>
        <p>Registration details: DOB {account.registeredDateOfBirth || "not supplied"}; temporary record {account.temporaryPatientIdentifier}</p>
        <label>Find the matching patient record
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search by name or patient ID" />
        </label>
        {search.trim().length > 1 && <div className="stack">
          {(patients.data?.items ?? []).filter((patient) => !String(patient.patientIdentifier).startsWith("SELF-")).map((patient) => <label className="panel" key={patient.id}>
            <input type="radio" name={`patient-${account.userId}`} checked={selection[account.userId] === String(patient.id)} onChange={() => setSelection({ ...selection, [account.userId]: String(patient.id) })} />
            {patient.firstName} {patient.lastName} · {patient.patientIdentifier} · DOB {patient.dateOfBirth || "not recorded"}
          </label>)}
          {patients.isLoading && <p>Searching records…</p>}
          <ErrorBox error={patients.error} />
        </div>}
        <label>Evidence reviewed (required)
          <textarea maxLength={300} value={evidence[account.userId] ?? ""} onChange={(event) => setEvidence({ ...evidence, [account.userId]: event.target.value })} placeholder="For example: confirmed identity using hospital record and photo ID" />
        </label>
        <button className="primary" disabled={!selection[account.userId] || !evidence[account.userId]?.trim() || link.isPending} onClick={() => link.mutate({ userId: account.userId, patientId: Number(selection[account.userId]), evidence: evidence[account.userId] })}>Link verified account</button>
      </section>)}
    </div>}
  </>;
}
