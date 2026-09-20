"use client";
import { useEffect, useState } from "react";
import { ArrowRight, CheckCircle2, AlertCircle } from "lucide-react";
import type { AdmissionView, RoomCapacity } from "../../api/contracts";
export function ProposalPreview({
  current,
  destination,
  actionType,
  expiresAt,
  onExpired,
}: {
  current?: AdmissionView;
  destination?: RoomCapacity;
  actionType: string;
  expiresAt: string;
  onExpired?: () => void;
}) {
  const [remaining, setRemaining] = useState(
    Math.max(0, Math.ceil((Date.parse(expiresAt) - Date.now()) / 1000)),
  );
  useEffect(() => {
    const tick = () => {
      const n = Math.max(
        0,
        Math.ceil((Date.parse(expiresAt) - Date.now()) / 1000),
      );
      setRemaining(n);
      if (!n) onExpired?.();
    };
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [expiresAt, onExpired]);
  const room = current?.rooms.find((r) => !r.assignment.releasedAt)?.room;
  return (
    <>
      <h3>{actionType === "TRANSFER" ? "Transfer proposal" : "Action proposal"}</h3>
      {current && <p><strong>{current.patient.firstName} {current.patient.lastName}</strong></p>}
      <div className="proposal-preview">
        <span>
          Current placement
          <strong>{room ? `Room ${room.roomNumber}` : "Not admitted"}</strong>
        </span>
        <ArrowRight />
        <span>
          Proposed placement
          <strong>
            {destination
              ? `Room ${destination.roomNumber}`
              : actionType === "DISCHARGE"
                ? "Discharged"
                : "Awaiting placement"}
          </strong>
        </span>
      </div>
      {destination && typeof destination.occupiedBeds === "number" && (
        <p>
          After transfer: {destination.occupiedBeds + 1} /{" "}
          {destination.bedCount} occupied.
        </p>
      )}
      <ul className="proposal-checks">
        {destination && <li>{destination.active && destination.availableBeds > 0 ? <CheckCircle2 size={17} /> : <AlertCircle size={17} />}Room {destination.roomNumber}: {destination.active ? `${destination.availableBeds} available capacity slots at proposal time` : "inactive"}</li>}
        {current && <li>{current.admission.status === "ACTIVE" ? <CheckCircle2 size={17} /> : <AlertCircle size={17} />}Admission {current.admission.status.toLowerCase()}</li>}
        <li><CheckCircle2 size={17} />Your confirmation is required before any write</li>
      </ul>
      <p>
        Admission, permissions and capacity are checked when preparing this
        proposal and rechecked on confirmation.
      </p>
      <p>
        Audit event:{" "}
        <code>
          {actionType === "TRANSFER"
            ? "ROOM_TRANSFERRED"
            : actionType === "DISCHARGE"
              ? "PATIENT_DISCHARGED"
              : "ADMISSION_CREATED"}
        </code>
      </p>
      <p className={remaining ? "proposal-expiry" : "error"} role="status">
        {remaining
          ? `Expires in ${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}`
          : "Proposal expired. Prepare a new request."}
      </p>
    </>
  );
}
