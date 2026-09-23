"use client";
import { BedDouble } from "../../icons";
import { date, type Row } from "../../api";

export function RoomCard({
  room: r,
  compact = false,
  onEdit,
  onManageHolds,
}: {
  room: Row;
  compact?: boolean;
  onEdit?: () => void;
  onManageHolds?: () => void;
}) {
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
      {holds.length > 0 && (
        <details className="room-holds">
          <summary>{holds.length} maintenance hold{holds.length === 1 ? "" : "s"}</summary>
          {holds.map((hold: Row) => (
            <p key={hold.id}>
              <strong>{hold.bedCount} bed{hold.bedCount === 1 ? "" : "s"}: {hold.reason}</strong>
              <small>
                {Date.parse(hold.startsAt) <= now ? "Active" : "Scheduled"} · {date(hold.startsAt)} to {date(hold.endsAt)}
              </small>
            </p>
          ))}
        </details>
      )}
    </div>
  );
}
