"use client";
import { fullName, money } from "../../api";
import type {
  Admission,
  AdmissionView,
  DashboardReport,
  Patient,
  ProcedureView,
} from "../../api/contracts";
import { Status } from "../../components/workspace";

/** The assistant returns one of the authorized report payloads; each field identifies its shape. */
type AiReportData = Partial<DashboardReport> & {
  rows?: ProcedureView[];
  totalCost?: number;
  admissions?: AdmissionView[];
  admission?: Admission;
  patient?: Patient;
};

export function AiReport({ data: d }: { data: AiReportData }) {
  if (d.activeAdmissions !== undefined)
    return (
      <div className="ai-stats">
        <span>
          <strong>{d.activeAdmissions}</strong>Active admissions
        </span>
        <span>
          <strong>{d.availableBeds}</strong>Available beds
        </span>
        <span>
          <strong>{d.proceduresToday}</strong>Procedures today
        </span>
      </div>
    );
  if (d.rows)
    return (
      <>
        <p>
          {d.rows.length} procedures · {money(d.totalCost)}
        </p>
        {d.rows.map((r) => (
          <div className="result-row" key={r.record.id}>
            <span>
              {r.procedure.procedureName}
              <small>{fullName(r.patient)}</small>
            </span>
            <strong>{money(r.record.priceAtExecution)}</strong>
          </div>
        ))}
      </>
    );
  if (d.admissions)
    return (
      <>
        {d.admissions.map((v) => (
          <div className="result-row" key={v.admission.id}>
            <span>{fullName(v.patient)}</span>
            <Status value={v.admission.status} />
          </div>
        ))}
      </>
    );
  if (d.admission)
    return (
      <p>
        {d.admission.admissionNumber} · {fullName(d.patient)}
      </p>
    );
  return <p>No matching records.</p>;
}
