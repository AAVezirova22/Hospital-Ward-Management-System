"use client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, fullName, date, money, type User } from "../../api";
import type { Patient, AdmissionView } from "../../api/contracts";
import { ThemeToggle } from "../../cinematic";
import { LoadingState } from "../../components/LoadingState";
import { PortalFollowUp } from "./PortalFollowUp";
import { RoomMovementHistory } from "./RoomMovementHistory";
export function PatientPortal({
  user,
  onLogout,
}: {
  user: User;
  onLogout: () => void;
}) {
  const client = useQueryClient();
  const [correction, setCorrection] = useState({ firstName: "", lastName: "", dateOfBirth: "", address: "", phoneNumber: "" });
  const [correctionMessage, setCorrectionMessage] = useState("");
  const [contact, setContact] = useState<{ address?: string; phoneNumber?: string }>({});
  const [contactMessage, setContactMessage] = useState("");
  const query = useQuery({
    queryKey: ["portal", user.id],
    queryFn: () =>
      api<{ patient: Patient; admissions: AdmissionView[] }>("/portal/me"),
  });
  const requests = useQuery({ queryKey: ["portal-corrections", user.id], queryFn: () => api<any[]>("/portal/correction-requests") });
  const address = contact.address ?? query.data?.patient.address ?? "";
  const phoneNumber = contact.phoneNumber ?? query.data?.patient.phoneNumber ?? "";
  const updateContact = useMutation({
    mutationFn: () => api<{ address: string | null; phoneNumber: string | null }>("/portal/me/contact", "PUT", { address, phoneNumber }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["portal", user.id] });
      setContact({});
      setContactMessage("Your contact details were saved.");
    },
  });
  const submitCorrection = useMutation({
    mutationFn: () => api("/portal/correction-requests", "POST", {
      ...correction,
      dateOfBirth: correction.dateOfBirth || null,
      firstName: correction.firstName || null,
      lastName: correction.lastName || null,
      address: correction.address || null,
      phoneNumber: correction.phoneNumber || null,
    }),
    onSuccess: async () => {
      setCorrection({ firstName: "", lastName: "", dateOfBirth: "", address: "", phoneNumber: "" });
      setCorrectionMessage("Your correction request was sent to the care team.");
      await client.invalidateQueries({ queryKey: ["portal-corrections", user.id] });
    },
  });
  return (
    <div className="patient-portal workspace">
      <header>
        <strong className="brand">medcore</strong>
        <div className="actions">
          <ThemeToggle />
          <button className="secondary" onClick={onLogout}>
            Sign out
          </button>
        </div>
      </header>
      <main>
        <h1>
          {query.data
            ? `Hello, ${query.data.patient.firstName}.`
            : "Your care history."}
        </h1>
        <p>Your admissions, attending doctors and recorded procedures.</p>
        {user.requestedRole === "DOCTOR" && (
          <section className="panel">
            <h2>Doctor access requested</h2>
            <p>
              Your email is verified. An administrator must approve access and
              link your doctor profile. Your patient workspace remains available
              while you wait.
            </p>
          </section>
        )}
        {query.isLoading && <LoadingState />}
        {query.error && (
          <p className="error" role="alert">
            {query.error.message}
          </p>
        )}
        {query.data && (
          <>
            <PortalFollowUp />
            <section className="panel">
              <h2>{fullName(query.data.patient)}</h2>
              <p>
                {query.data.patient.patientIdentifier} · Date of birth{" "}
                {query.data.patient.dateOfBirth}
              </p>
              <button className="secondary" onClick={() => window.print()}>
                Print my summary
              </button>
            </section>
            <section className="panel">
              <h2>Edit your contact details</h2>
              <p>Update your address and phone number directly. Name and date of birth changes are reviewed by your care team below.</p>
              <form className="form-grid" onSubmit={(event) => { event.preventDefault(); setContactMessage(""); updateContact.mutate(); }}>
                <label>Address<input autoComplete="street-address" value={address} onChange={(event) => setContact({ ...contact, address: event.target.value })} maxLength={500} /></label>
                <label>Phone number<input type="tel" autoComplete="tel" value={phoneNumber} onChange={(event) => setContact({ ...contact, phoneNumber: event.target.value })} maxLength={40} pattern="[+0-9().\\-\\s]{3,40}" title="Use digits and common phone punctuation." /></label>
                <button className="primary" disabled={updateContact.isPending}>{updateContact.isPending ? "Saving…" : "Save contact details"}</button>
              </form>
              {contactMessage && <p role="status">{contactMessage}</p>}
              {updateContact.error && <p className="error" role="alert">{updateContact.error.message}</p>}
            </section>
            <section className="panel">
              <h2>Request a profile correction for staff review</h2>
              <p>Changes are reviewed by your care team before they update your record. Leave fields blank if they do not need correction.</p>
              <form className="form-grid" onSubmit={(event) => { event.preventDefault(); setCorrectionMessage(""); submitCorrection.mutate(); }}>
                <label>First name<input value={correction.firstName} onChange={(event) => setCorrection({ ...correction, firstName: event.target.value })} maxLength={100} /></label>
                <label>Last name<input value={correction.lastName} onChange={(event) => setCorrection({ ...correction, lastName: event.target.value })} maxLength={100} /></label>
                <label>Date of birth<input type="date" value={correction.dateOfBirth} onChange={(event) => setCorrection({ ...correction, dateOfBirth: event.target.value })} /></label>
                <label>Address<input value={correction.address} onChange={(event) => setCorrection({ ...correction, address: event.target.value })} maxLength={500} /></label>
                <label>Phone number<input value={correction.phoneNumber} onChange={(event) => setCorrection({ ...correction, phoneNumber: event.target.value })} maxLength={40} /></label>
                <button className="primary" disabled={submitCorrection.isPending}>Submit for review</button>
              </form>
              {correctionMessage && <p role="status">{correctionMessage}</p>}
              {submitCorrection.error && <p className="error" role="alert">{submitCorrection.error.message}</p>}
              <h3>Your requests</h3>
              {requests.data?.length ? requests.data.map((request) => <div className="result-row" key={request.id}><span>{[request.firstName, request.lastName, request.dateOfBirth, request.address, request.phoneNumber].filter(Boolean).join(" · ")}<small>{request.createdAt ? new Date(request.createdAt).toLocaleString() : ""}{request.reviewNote ? ` · ${request.reviewNote}` : ""}</small></span><strong>{request.status}</strong></div>) : <p>No correction requests yet.</p>}
            </section>
            {!query.data.admissions.length && (
              <section className="panel">
                <h2>You’re all set.</h2>
                <p>
                  No admissions are linked to your new account yet. Your care
                  team can use your patient identifier when recording an
                  admission.
                </p>
              </section>
            )}
            {query.data.admissions.map((v) => (
              <section className="panel" key={v.admission.id}>
                <span className="status">{v.admission.status}</span>
                <h2>{v.admission.admissionNumber}</h2>
                <p>
                  Admitted {date(v.admission.admissionDateTime)}
                  {v.admission.dischargeDateTime &&
                    ` · Discharged ${date(v.admission.dischargeDateTime)}`}
                </p>
                <p>Attending doctor: {fullName(v.doctor)}</p>
                {v.admission.expectedDischargeDate && (
                  <p>Expected discharge: {v.admission.expectedDischargeDate}</p>
                )}
                <RoomMovementHistory rooms={v.rooms} />
                <h3>Recorded procedures</h3>
                {v.procedures.map((p) => (
                  <div className="result-row" key={p.record.id}>
                    <span>
                      {p.procedure.procedureName}
                      <small>{date(p.record.performedAt)}</small>
                    </span>
                    <strong>{money(p.record.priceAtExecution)}</strong>
                  </div>
                ))}
                {!v.procedures.length && <p>No procedures recorded.</p>}
                <p>Total recorded cost: {money(v.totalCost)}</p>
              </section>
            ))}
          </>
        )}
      </main>
    </div>
  );
}
