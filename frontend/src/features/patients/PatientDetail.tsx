"use client";
import React, { useState, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter, usePathname } from "next/navigation";
import { api, fullName, money, date, type Row, type User } from "../../api";
import {
  Link,
  useUser,
  useData,
  ErrorBox,
  Empty,
  Status,
  Modal,
  Title,
} from "../../components/workspace";
import { Plus, MoveRight, ArrowRight } from "../../icons";
import { EntityForm } from "../administration/EntityForm";
import { Workflow } from "../admissions/Workflow";
import { CareTimeline } from "./CareTimeline";
import { PatientDocumentDraft } from "./PatientDocumentDraft";
import { PatientPathways } from "../care-pathways/PatientPathways";
export function PatientDetail({ id }: { id: string }) {
  const { data, error, isLoading } = useData("/patients/" + id);
  const [edit, setEdit] = useState(false),
    [documentDraft, setDocumentDraft] = useState(false),
    [flow, setFlow] = useState<string | null>(null);
  const [generatedAt, setGeneratedAt] = useState("");
  useEffect(() => {
    const refreshGeneratedAt = () => setGeneratedAt(new Date().toLocaleString());
    refreshGeneratedAt();
    window.addEventListener("beforeprint", refreshGeneratedAt);
    return () => window.removeEventListener("beforeprint", refreshGeneratedAt);
  }, []);
  const user = useUser();
  if (isLoading)
    return <div className="skeleton">Opening patient dossier…</div>;
  if (error) return <ErrorBox error={error} />;
  if (!data) return null;
  const p = data.patient,
    active = data.admissions.find((v: Row) => v.admission.status === "ACTIVE");
  return (
    <div className="patient-detail-page">
      <article className="patient-print-summary" aria-label="Printable patient summary">
        <header>
          <p>Patient and discharge summary</p>
          <h1>{fullName(p)}</h1>
          <dl className="patient-print-identifiers">
            <div><dt>Patient ID</dt><dd>{p.patientIdentifier}</dd></div>
            <div><dt>Date of birth</dt><dd>{p.dateOfBirth || "Not recorded"}</dd></div>
            <div><dt>Phone</dt><dd>{p.phoneNumber || "Not recorded"}</dd></div>
            <div><dt>Address</dt><dd>{p.address || "Not recorded"}</dd></div>
          </dl>
          <small>Generated {generatedAt}</small>
        </header>
        <h2>Admission and room history</h2>
        {data.admissions.length === 0 ? <p>No hospitalizations recorded.</p> : data.admissions.map((v: Row) => (
          <section className="patient-print-stay" key={v.admission.id}>
            <h3>{v.admission.admissionNumber} · {v.admission.status}</h3>
            <p>{date(v.admission.admissionDateTime)} – {v.admission.dischargeDateTime ? date(v.admission.dischargeDateTime) : "Currently admitted"}</p>
            <ul>
              {v.rooms.map((r: Row) => (
                <li key={r.assignment.id}>Room {r.room.roomNumber}: {date(r.assignment.assignedAt)}{r.assignment.releasedAt ? ` – ${date(r.assignment.releasedAt)}` : " – Current placement"}{r.assignment.reason ? ` · ${r.assignment.reason}` : ""}</li>
              ))}
            </ul>
            <h4>Procedures</h4>
            {v.procedures.length === 0 ? <p>None recorded.</p> : <ul>{v.procedures.map((r: Row) => (
              <li key={r.record.id}>{r.procedure.procedureName} · {date(r.record.performedAt)} · Dr. {fullName(r.doctor)}</li>
            ))}</ul>}
          </section>
        ))}
      </article>
      <Link className="back" to="/app/patients">
        ← Patient directory
      </Link>
      <Title
        eyebrow={"Patient dossier / " + p.patientIdentifier}
        title={fullName(p)}
        description={
          "Born " +
          p.dateOfBirth +
          " · " +
          (p.phoneNumber || "No phone recorded")
        }
      >
        <button className="secondary" onClick={() => setDocumentDraft(true)}>
          Review document
        </button>
        {user.role !== "DOCTOR" && (
          <div className="actions">
            <button className="secondary" onClick={() => setEdit(true)}>
              Edit details
            </button>
            {!active && (
              <button className="primary" onClick={() => setFlow("admit")}>
                <Plus size={17} />
                Admit patient
              </button>
            )}
          </div>
        )}
      </Title>
      <Link className="secondary" to={`/app/care-pathways?patientId=${p.id}`}>
        Plan follow-up pathway
      </Link>
      {documentDraft && (
        <PatientDocumentDraft
          initialPatient={p}
          onClose={() => setDocumentDraft(false)}
        />
      )}
      <button className="secondary" onClick={() => {
        setGeneratedAt(new Date().toLocaleString());
        window.requestAnimationFrame(() => window.print());
      }}>
        Print patient / discharge summary
      </button>
      <div className="detail-banner">
        <span>
          <small>PATIENT ID</small>
          {p.patientIdentifier}
        </span>
        <span>
          <small>ADDRESS</small>
          {p.address || "Not recorded"}
        </span>
        <span>
          <small>RECORDED STAYS</small>
          {data.admissions.length}
        </span>
        <span>
          <small>CURRENT STATUS</small>
          <Status value={active ? "ACTIVE" : "NOT ADMITTED"} />
        </span>
      </div>
      {active && (
        <section className="active-stay">
          <div>
            <span className="eyebrow">Current admission</span>
            <h2>
              Room{" "}
              {
                active.rooms.find((r: Row) => !r.assignment.releasedAt)?.room
                  .roomNumber
              }
            </h2>
            <p>
              Dr. {fullName(active.doctor)} · Since{" "}
              {date(active.admission.admissionDateTime)}
            </p>
          </div>
          <div className="actions">
            <button className="secondary" onClick={() => setFlow("procedure")}>
              Record procedure
            </button>
            {user.role !== "DOCTOR" && (
              <>
                <button className="secondary" onClick={() => setFlow("doctor")}>
                  Assign doctor
                </button>
                <button
                  className="secondary"
                  onClick={() => setFlow("transfer")}
                >
                  <MoveRight size={17} />
                  Transfer
                </button>
                <button
                  className="primary"
                  onClick={() => setFlow("discharge")}
                >
                  Discharge
                  <ArrowRight size={17} />
                </button>
              </>
            )}
          </div>
        </section>
      )}
      <CareTimeline admissions={data.admissions} />
      <PatientPathways patientId={p.id} />
      <details className="care-record-details">
        <summary>Detailed admission records and costs</summary>
        <div className="section-heading">
          <div>
            <span className="eyebrow">Operational history</span>
            <h2>Admission timeline</h2>
          </div>
        </div>
        {data.admissions.length === 0 ? (
          <Empty text="No hospitalizations recorded for this patient." />
        ) : (
          data.admissions.map((v: Row) => (
            <section className="panel stay" key={v.admission.id}>
              <div className="section-heading">
                <div>
                  <h3>{v.admission.admissionNumber}</h3>
                  <p>
                    {date(v.admission.admissionDateTime)} →{" "}
                    {v.admission.dischargeDateTime
                      ? date(v.admission.dischargeDateTime)
                      : "Present"}
                  </p>
                </div>
                <Status value={v.admission.status} />
              </div>
              <div className="stay-body">
                <div>
                  <span className="eyebrow">Room movements</span>
                  <ol className="timeline">
                    {v.rooms.map((r: Row) => (
                      <li key={r.assignment.id}>
                        <span>Room {r.room.roomNumber}</span>
                        <small>
                          {date(r.assignment.assignedAt)}
                          {r.assignment.releasedAt
                            ? " → " + date(r.assignment.releasedAt)
                            : " · Current placement"}
                        </small>
                        <p>{r.assignment.reason}</p>
                      </li>
                    ))}
                  </ol>
                </div>
                <div>
                  <div className="section-heading">
                    <span className="eyebrow">Performed procedures</span>
                    <strong>{money(v.totalCost)}</strong>
                  </div>
                  {v.procedures.length === 0 ? (
                    <p className="muted">No procedures recorded.</p>
                  ) : (
                    v.procedures.map((r: Row) => (
                      <div className="procedure-record" key={r.record.id}>
                        <strong>{r.procedure.procedureName}</strong>
                        <span>{money(r.record.priceAtExecution)}</span>
                        <small>
                          {date(r.record.performedAt)} · Dr.{" "}
                          {fullName(r.doctor)}
                        </small>
                        {r.record.note && <p>{r.record.note}</p>}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </section>
          ))
        )}
      </details>
      {edit && (
        <EntityForm kind="patients" record={p} onClose={() => setEdit(false)} />
      )}{" "}
      {flow && (
        <Workflow
          kind={flow}
          patient={p}
          active={active}
          onClose={() => setFlow(null)}
        />
      )}
    </div>
  );
}
