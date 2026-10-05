"use client";
import { useRouter } from "next/navigation";
import { fullName, money, date, patientHref, type Row } from "../../api";
import { Status } from "../../components/workspace";
import { ArrowUpRight, ArrowRight } from "../../icons";
import { safeRoute } from "../../ai-contract";
import { ProposalPreview } from "./ProposalPreview";
import type { AdmissionView, RoomCapacity } from "../../api/contracts";
import { appointmentTime } from "../../date-time";

function asRow(value: unknown): Row {
  return (value && typeof value === "object" ? value : {}) as Row;
}
function asRows(value: unknown): Row[] {
  return Array.isArray(value) ? (value as Row[]) : [];
}

export function AiReport({ data: d }: { data: Row }) {
  if (typeof d.available === "boolean")
    return (
      <p>
        {d.available ? "Available" : "Unavailable"}: {d.reason} Dr.{" "}
        {fullName(d.doctor)} · {appointmentTime(d.startsAt, d.timeZone)} ·{" "}
        {d.durationMinutes} minutes ({d.timeZone}). No time has been reserved.
      </p>
    );
  if (d.appointments)
    return (
      <>
        <p>
          {d.appointments.totalElements} appointments · {d.timeZone}
        </p>
        {asRows(d.appointments.items).map((a) => (
          <div className="result-row" key={a.id}>
            <span>
              {a.attendeeName}
              <small>
                Dr. {fullName(a.doctor)} ·{" "}
                {appointmentTime(a.startsAt, a.timeZone)} · {a.durationMinutes}{" "}
                minutes
              </small>
            </span>
            <Status value={a.status} />
          </div>
        ))}
        {d.appointments.hasNext && (
          <p>
            Showing the first page. Open Doctors → Appointments to view the
            complete schedule.
          </p>
        )}
      </>
    );
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
          <strong>{d.heldBeds ?? 0}</strong>Beds held for maintenance
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
      {r.responseType === "ROOM_LIST" && (
        <>
          {asRows(d.requiredCapabilities).length > 0 && (
            <p>
              Required capabilities: {asRows(d.requiredCapabilities).join(", ")}
              .
            </p>
          )}
          {asRows(d.rooms).map((room: Row) => (
            <div className="result-row" key={room.id}>
              <span>
                Room {String(room.roomNumber ?? "")}
                {asRows(room.capabilities).length > 0 && (
                  <small>
                    Capabilities: {asRows(room.capabilities).join(", ")}
                  </small>
                )}
              </span>
              <strong>{String(room.availableBeds ?? 0)} free</strong>
            </div>
          ))}
          {asRows(d.excludedRooms).map((room: Row) => (
            <div className="result-row" key={room.id}>
              <span>
                Room {String(room.roomNumber ?? "")}
                <small>
                  {String(room.reason ?? "Excluded from placement search.")}
                </small>
              </span>
              <strong>Excluded</strong>
            </div>
          ))}
        </>
      )}
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
      {r.responseType === "REPORT_RESULT" && <AiReport data={d} />}
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
          <h3>
            {action.actionType === "APPOINTMENT"
              ? String(asRow(d.appointment).attendeeName)
              : fullName(asRow(d.patient))}
          </h3>
          {action.actionType === "APPOINTMENT" ? (
            <>
              <p>Doctor: Dr. {fullName(asRow(asRow(d.appointment).doctor))}</p>
              <p>
                {appointmentTime(
                  String(asRow(d.appointment).startsAt),
                  String(asRow(d.appointment).timeZone),
                )}{" "}
                · {String(asRow(d.appointment).durationMinutes)} minutes (
                {String(asRow(d.appointment).timeZone)})
              </p>
              {asRow(d.appointment).contact && (
                <p>Contact: {String(asRow(d.appointment).contact)}</p>
              )}
              {asRow(d.appointment).notes && (
                <p>Notes: {String(asRow(d.appointment).notes)}</p>
              )}
              <p>
                This time is not reserved until you confirm. Availability and
                access are checked again on confirmation.
              </p>
            </>
          ) : (
            <ProposalPreview
              current={current}
              destination={destination}
              actionType={String(action.actionType ?? "")}
              requiredRoomCapabilities={
                Array.isArray(d.requiredRoomCapabilities)
                  ? d.requiredRoomCapabilities.map(String)
                  : (current?.admission.requiredRoomCapabilities ?? [])
              }
              expiresAt={String(action.expiresAt ?? "")}
            />
          )}
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
