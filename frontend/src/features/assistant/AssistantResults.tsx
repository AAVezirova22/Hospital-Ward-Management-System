"use client";
import { fullName, money, type Row } from "../../api";
import { Status } from "../../components/workspace";

export function AiReport({ data: d }: { data: Row }) {
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
          {(d.rows as Row[]).length} procedures · {money(d.totalCost)}
        </p>
        {(d.rows as Row[]).map((r: Row) => (
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
        {(d.admissions as Row[]).map((v: Row) => (
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
