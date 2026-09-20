"use client";
import React from "react";
import type { RoomCapacity } from "../../api/contracts";
import { BedDouble, ArrowUpRight } from "lucide-react";
export function RoomCard({
  room: r,
  compact = false,
  onEdit,
}: {
  room: RoomCapacity;
  compact?: boolean;
  onEdit?: () => void;
}) {
  return (
    <div className={"room-card " + (!r.availableBeds ? "full" : "")}>
      <div className="room-card-top">
        <span>
          ROOM <b>{r.roomNumber}</b>
        </span>
        {onEdit ? (
          <button className="text-button" onClick={onEdit}>
            Edit
          </button>
        ) : (
          <BedDouble size={16} />
        )}
      </div>
      <div className="beds">
        {Array.from({ length: Math.min(r.bedCount, 12) }, (_, i) => (
          <span key={i} className={i < r.occupiedBeds ? "occupied" : ""}>
            <BedDouble size={compact ? 19 : 24} />
          </span>
        ))}
        {r.bedCount > 12 && <small>+{r.bedCount - 12}</small>}
      </div>
      <div className="room-card-bottom">
        <span className={r.availableBeds ? "available" : "muted"}>
          {r.active
            ? r.availableBeds
              ? `${r.availableBeds} available`
              : "At capacity"
            : "Inactive"}
        </span>
        <small>
          {r.occupiedBeds} / {r.bedCount}
        </small>
      </div>
    </div>
  );
}
