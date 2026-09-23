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
import { BedDouble, ArrowUpRight } from "../../icons";
export function RoomCard({
  room: r,
  compact = false,
  onEdit,
}: {
  room: Row;
  compact?: boolean;
  onEdit?: () => void;
}) {
  const capabilities = Array.isArray(r.capabilities) ? r.capabilities : [];
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
      {capabilities.length > 0 && (
        <ul className="room-capabilities" aria-label="Room capabilities">
          {capabilities.map((capability: string) => (
            <li key={capability}>{capability}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
