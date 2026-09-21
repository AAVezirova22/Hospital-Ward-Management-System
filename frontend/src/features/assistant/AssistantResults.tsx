"use client";
import { useRouter } from "next/navigation";
import { fullName, money, date, patientHref, type Row } from "../../api";
import { Status } from "../../components/workspace";
import { ArrowUpRight, ArrowRight } from "../../icons";
import { safeRoute } from "../../ai-contract";
import { ProposalPreview } from "./ProposalPreview";
import type {
  Admission,
  AdmissionView,
  DashboardReport,
  Patient,
  ProcedureView,
  RoomCapacity,
} from "../../api/contracts";

function asRow(value: unknown): Row {
  return (value && typeof value === "object" ? value : {}) as Row;
}
function asRows(value: unknown): Row[] {
  return Array.isArray(value) ? (value as Row[]) : [];
}

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

export function AssistantTurn({
  r,
  i,
  busy,
  onClose,
  onAction,
}: {
  r: Row;
  i: number;
  busy: boolean;
  onClose: () => void;
  onAction: (id: number, op: string, index: number) => void;
}) {
  const router = useRouter();
  const d = asRow(r.data);
  const action = asRow(d.action);
  const current = d.current as AdmissionView | undefined;
  const destination = d.destination as RoomCapacity | undefined;
  return (
    <div className="ai-result">
      <div className="ai-query">› {String(r.query ?? "")}</div>
      <p>{String(r.message ?? "")}</p>
      <small className="ai-mode">
        {r.model === "local-command-model"
          ? "Local command mode"
          : "Configured model"}{" "}
        · Backend-authorized results
      </small>
      {r.responseType === "PATIENT_LIST" &&
        asRows(d.patients).map((p: Row) => (
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
              <small>{String(p.patientIdentifier ?? "")}</small>
            </span>
            <ArrowUpRight size={16} />
          </button>
        ))}
      {r.responseType === "ROOM_LIST" &&
        asRows(d.rooms).map((room: Row) => (
          <div className="result-row" key={room.id}>
            <span>Room {String(room.roomNumber ?? "")}</span>
            <strong>{String(room.availableBeds ?? 0)} free</strong>
          </div>
        ))}
      {r.responseType === "PATIENT_SUMMARY" && (
        <>
          <h3>{fullName(asRow(d.patient))}</h3>
          <p>{asRows(d.admissions).length} recorded hospitalizations.</p>
          {asRows(d.admissions).map((v: Row) => (
            <div className="result-row" key={asRow(v.admission).id}>
              <span>
                {String(asRow(v.admission).admissionNumber ?? "")}
                <small>Dr. {fullName(asRow(v.doctor))}</small>
              </span>
              <Status value={String(asRow(v.admission).status ?? "")} />
            </div>
          ))}
          <button
            className="secondary"
            onClick={() => {
              router.push(patientHref(asRow(d.patient)));
              onClose();
            }}
          >
            Open dossier
          </button>
        </>
      )}
      {r.responseType === "REPORT_RESULT" && (
        <AiReport data={d as AiReportData} />
      )}
      {r.responseType === "NAVIGATION_COMMAND" && (
        <button
          className="primary"
          onClick={() => {
            if (safeRoute.safeParse(d.route).success) {
              router.push(String(d.route));
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
            {String(action.actionType ?? "")} proposal
          </span>
          <h3>{fullName(asRow(d.patient))}</h3>
          <ProposalPreview
            current={current}
            destination={destination}
            actionType={String(action.actionType ?? "")}
            expiresAt={String(action.expiresAt ?? "")}
          />
          {current && (
            <p>
              Current room:{" "}
              {
                current.rooms.find((x) => !x.assignment.releasedAt)?.room
                  .roomNumber
              }
            </p>
          )}
          {destination && <p>Destination: Room {destination.roomNumber}</p>}
          {d.doctor && <p>Doctor: {fullName(asRow(d.doctor))}</p>}
          <p>Expires {date(String(action.expiresAt ?? ""))}</p>
          {r.done ? (
            <Status value={String(r.done)} />
          ) : (
            <div className="actions">
              <button
                className="secondary"
                disabled={busy}
                onClick={() => onAction(Number(action.id), "cancel", i)}
              >
                Cancel proposal
              </button>
              <button
                className="primary"
                disabled={
                  busy ||
                  Date.parse(String(action.expiresAt ?? "")) <= Date.now()
                }
                onClick={() => onAction(Number(action.id), "confirm", i)}
              >
                Confirm {String(action.actionType ?? "").toLowerCase()}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
