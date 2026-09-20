"use client";
import React, { useState } from "react";
import { fullName, date, patientHref } from "../../api";
import type { AdmissionView } from "../../api/contracts";
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
import { ArrowUpRight, ClipboardList } from "lucide-react";
export function Admissions() {
  const { data, error, isLoading } = useData<AdmissionView[]>("/admissions");
  const [active, setActive] = useState(true);
  return (
    <>
      <Title
        eyebrow="HOSPITALIZATION"
        title="Every stay, connected."
        description="Follow admissions from placement through transfer and discharge."
      />
      <div className="toolbar">
        <ClipboardList size={18} />
        <span>Admission register</span>
        <label className="inline-check">
          <input
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
          />
          Active only
        </label>
      </div>
      <ErrorBox error={error} />
      <div className="panel table-panel">
        <table>
          <thead>
            <tr>
              <th scope="col">Patient</th>
              <th scope="col">Admission</th>
              <th scope="col">Attending doctor</th>
              <th scope="col">Room</th>
              <th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            {Array.isArray(data) &&
              data
                .filter((v) => !active || v.admission.status === "ACTIVE")
                .map((v) => (
                  <tr key={v.admission.id}>
                    <td>
                      <Link to={patientHref(v.patient)}>
                        {fullName(v.patient)}
                        <ArrowUpRight size={14} />
                      </Link>
                    </td>
                    <td>
                      <span className="mono">
                        {v.admission.admissionNumber}
                      </span>
                      <small>{date(v.admission.admissionDateTime)}</small>
                    </td>
                    <td>Dr. {fullName(v.doctor)}</td>
                    <td>
                      {v.rooms.find((r) => !r.assignment.releasedAt)?.room
                        .roomNumber || "Not assigned"}
                    </td>
                    <td>
                      <Status value={v.admission.status} />
                    </td>
                  </tr>
                ))}
          </tbody>
        </table>
        {isLoading && <div className="skeleton">Loading admissions…</div>}
        {Array.isArray(data) &&
          data.filter((v) => !active || v.admission.status === "ACTIVE")
            .length === 0 && <Empty />}
      </div>
    </>
  );
}
