"use client";
import { useQuery } from "@tanstack/react-query";
import { api, fullName, date, money, type User } from "../../api";
import type { Patient, AdmissionView } from "../../api/contracts";
import { ThemeToggle } from "../../cinematic";
import { LoadingState } from "../../components/LoadingState";
import { PortalFollowUp } from "./PortalFollowUp";
export function PatientPortal({
  user,
  onLogout,
}: {
  user: User;
  onLogout: () => void;
}) {
  const query = useQuery({
    queryKey: ["portal", user.id],
    queryFn: () =>
      api<{ patient: Patient; admissions: AdmissionView[] }>("/portal/me"),
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
