"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { X, ArrowRight } from "lucide-react";
import { date, fullName, patientHref } from "../../api";
import type { AdmissionView } from "../../api/contracts";
import { useUser } from "../../components/workspace";
import { Workflow } from "../admissions/Workflow";

export function BedDrawer({
  admission,
  onClose,
}: {
  admission: AdmissionView;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [transfer, setTransfer] = useState(false);
  const user = useUser();
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  const room = admission.rooms.find((r) => !r.assignment.releasedAt)?.room;
  return (
    <>
      <dialog
        ref={ref}
        className="bed-drawer"
        aria-labelledby="bed-drawer-title"
        onCancel={onClose}
      >
        <div className="modal-title">
          <span className="eyebrow">Current placement</span>
          <button
            className="icon"
            aria-label="Close patient details"
            onClick={onClose}
          >
            <X />
          </button>
        </div>
        <h2 id="bed-drawer-title">{fullName(admission.patient)}</h2>
        <p className="muted">{admission.patient.patientIdentifier}</p>
        <div className="drawer-placement">
          Room {room?.roomNumber ?? "unavailable"}
        </div>
        <dl className="drawer-facts">
          <dt>Attending doctor</dt>
          <dd>Dr. {fullName(admission.doctor)}</dd>
          <dt>Admitted</dt>
          <dd>{date(admission.admission.admissionDateTime)}</dd>
          <dt>Expected discharge</dt>
          <dd>
            {admission.admission.expectedDischargeDate ?? "Not scheduled"}
          </dd>
        </dl>
        <Link
          className="secondary"
          href={patientHref(admission.patient)}
        >
          View patient timeline <ArrowRight size={16} />
        </Link>
        {user.role !== "DOCTOR" && admission.admission.status === "ACTIVE" && (
          <button className="primary" onClick={() => setTransfer(true)}>
            Prepare transfer <ArrowRight size={16} />
          </button>
        )}
        <p className="muted">
          Transfers require review and confirmation. Capacity is checked again
          when saved.
        </p>
      </dialog>
      {transfer && (
        <Workflow
          kind="transfer"
          patient={admission.patient}
          active={admission}
          onClose={() => {
            setTransfer(false);
            onClose();
          }}
        />
      )}
    </>
  );
}
