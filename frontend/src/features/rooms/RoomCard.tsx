"use client";
import { BedDouble } from "../../icons";
import { date, fullName, patientHref, type Row } from "../../api";
import type { AdmissionView } from "../../api/contracts";
import { Link } from "../../components/workspace";

export function RoomCard({
  room: r,
  compact = false,
  onEdit,
  onManageHolds,
  occupants = [],
  occupantsLoading = false,
  occupantsError = false,
}: {
  room: Row;
  compact?: boolean;
  onEdit?: () => void;
  onManageHolds?: () => void;
  occupants?: AdmissionView[];
  occupantsLoading?: boolean;
  occupantsError?: boolean;
}) {
  const capabilities = Array.isArray(r.capabilities) ? r.capabilities : [];
  const held = r.heldBeds ?? 0;
  const holds = Array.isArray(r.holds) ? r.holds : [];
  const now = Date.now();
  return (
    <div className={"room-card " + (!r.availableBeds ? "full" : "")}>
      <div className="room-card-top">
        <span>
          ROOM <b>{r.roomNumber}</b>
        </span>
        <div>
          {onEdit && (
            <button className="text-button" onClick={onEdit}>
              Edit
            </button>
          )}
          {onManageHolds ? (
            <button className="text-button" onClick={onManageHolds}>
              Manage holds
            </button>
          ) : !onEdit ? (
            <BedDouble size={16} />
          ) : null}
        </div>
      </div>
      <div className="beds" aria-label={`${r.bedCount} bed capacity slots`}>
        {Array.from({ length: Math.min(r.bedCount, 12) }, (_, i) => {
          const state = i < r.occupiedBeds ? "occupied" : i < r.occupiedBeds + held ? "held" : "";
          return (
            <span key={i} className={state}>
              <BedDouble size={compact ? 19 : 24} />
            </span>
          );
        })}
        {r.bedCount > 12 && <small>+{r.bedCount - 12}</small>}
      </div>
      <div className="room-card-bottom">
        <span className={r.availableBeds ? "available" : "muted"}>
          {r.active
            ? r.availableBeds
              ? `${r.availableBeds} available`
              : "No placement capacity"
            : "Inactive"}
        </span>
        <small>
          {r.occupiedBeds} occupied · {held} held / {r.bedCount}
        </small>
      </div>
{capabilities.length > 0 && (
  <ul className="room-capabilities" aria-label="Room capabilities">
    {capabilities.map((capability: string) => (
      <li key={capability}>{capability}</li>
    ))}
  </ul>
)}

<details className="room-occupants">
  <summary>Room details{!occupantsLoading && !occupantsError ? ` · ${occupants.length} current patient${occupants.length === 1 ? "" : "s"}` : ""}</summary>
  {occupantsError ? (
    <p role="alert">Current occupants could not be loaded.</p>
  ) : occupantsLoading ? (
    <p role="status">Loading current occupants…</p>
  ) : occupants.length ? (
    <ul aria-label={`Current occupants of room ${r.roomNumber}`}>
      {occupants.map(({ admission, patient, doctor }) => (
        <li key={admission.id}>
          <Link to={patientHref(patient)}>{fullName(patient)}</Link>
          <span>
            <Link to={`/app/admissions?q=${encodeURIComponent(admission.admissionNumber)}`}>
              Admission {admission.admissionNumber}
            </Link>
          </span>
          <small>Attending doctor: Dr. {fullName(doctor)}</small>
        </li>
      ))}
    </ul>
  ) : (
    <p>No current patients are assigned to this room.</p>
  )}
</details>

{holds.length > 0 && (
  <details className="room-holds">
    <summary>
      {holds.length} maintenance hold{holds.length === 1 ? "" : "s"}
    </summary>

    {holds.map((hold: Row) => (
      <p key={hold.id}>
        <strong>
          {hold.bedCount} bed{hold.bedCount === 1 ? "" : "s"}: {hold.reason}
        </strong>
        <small>
          {Date.parse(hold.startsAt) <= now ? "Active" : "Scheduled"} ·{" "}
          {date(hold.startsAt)} to {date(hold.endsAt)}
        </small>
      </p>
    ))}
  </details>
)}
    </div>
  );
}
