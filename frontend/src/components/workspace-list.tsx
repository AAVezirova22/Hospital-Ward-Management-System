"use client";
import {
  Building2,
  Copy,
  KeyRound,
  Plus,
} from "lucide-react";
import type { WorkspaceHospital } from "../api/contracts";

type Props = {
  hospitals: WorkspaceHospital[];
  currentDepartmentId?: number;
  busy: boolean;
  onOpenDepartment: (id: number) => void;
  onReveal: (hospital: boolean, id: number) => void;
  onRotate: (
    hospital: boolean,
    id: number,
    options?: { expiresInHours?: number; singleUse?: boolean },
  ) => void;
  onLeaveDepartment: (id: number) => void;
  onLeaveHospital: (id: number) => void;
  onNewDepartment: (hospital: WorkspaceHospital) => void;
  onJoin: () => void;
  onCreateHospital: () => void;
};

export function WorkspaceList({
  hospitals,
  currentDepartmentId,
  busy,
  onOpenDepartment,
  onReveal,
  onRotate,
  onLeaveDepartment,
  onLeaveHospital,
  onNewDepartment,
  onJoin,
  onCreateHospital,
}: Props) {
  return (
    <div className="workspace-list">
      {hospitals.map((hospital) => (
        <section key={hospital.id}>
          <header>
            <Building2 size={16} />
            <strong>{hospital.name}</strong>
            {hospital.owner && <span>Owner</span>}
          </header>
          {hospital.departments.length === 0 && (
            <p className="muted">
              Join a department with a code to open its records.
            </p>
          )}
          <ul>
            {hospital.departments.map((department) => (
              <li key={department.id}>
                <button
                  type="button"
                  className={
                    department.id === currentDepartmentId
                      ? "selected"
                      : "secondary"
                  }
                  onClick={() => onOpenDepartment(department.id)}
                >
                  {department.name}
                  <small>
                    {department.role.replaceAll("_", " ").toLowerCase()}
                  </small>
                </button>
                {department.hasJoinCode && (
                  <p className="workspace-code">
                    Department join code
                    <button
                      type="button"
                      className="icon"
                      aria-label="Copy department join code"
                      onClick={() => onReveal(false, department.id)}
                    >
                      <Copy size={14} />
                    </button>
                    <button
                      type="button"
                      className="text-button"
                      disabled={busy}
                      onClick={() => onRotate(false, department.id)}
                    >
                      Replace
                    </button>
                    <button
                      type="button"
                      className="text-button"
                      disabled={busy}
                      onClick={() =>
                        onRotate(false, department.id, {
                          expiresInHours: 24,
                          singleUse: true,
                        })
                      }
                    >
                      One-time 24h
                    </button>
                  </p>
                )}
                <button
                  type="button"
                  className="text-button"
                  disabled={busy}
                  onClick={() => onLeaveDepartment(department.id)}
                >
                  Leave department
                </button>
              </li>
            ))}
          </ul>
          {hospital.owner && hospital.hasJoinCode && (
            <p className="workspace-code">
              Hospital join code
              <button
                type="button"
                className="icon"
                aria-label="Copy hospital join code"
                onClick={() => onReveal(true, hospital.id)}
              >
                <Copy size={14} />
              </button>
              <button
                type="button"
                className="text-button"
                disabled={busy}
                onClick={() => onRotate(true, hospital.id)}
              >
                Replace
              </button>
              <button
                type="button"
                className="text-button"
                disabled={busy}
                onClick={() =>
                  onRotate(true, hospital.id, {
                    expiresInHours: 24,
                    singleUse: true,
                  })
                }
              >
                One-time 24h
              </button>
            </p>
          )}
          <button
            type="button"
            className="text-button"
            disabled={busy}
            onClick={() => onLeaveHospital(hospital.id)}
          >
            Leave hospital
          </button>
          {hospital.owner && (
            <button
              type="button"
              className="text-button"
              onClick={() => onNewDepartment(hospital)}
            >
              <Plus size={14} />
              New department
            </button>
          )}
        </section>
      ))}
      <div className="workspace-actions">
        <button type="button" className="secondary" onClick={onJoin}>
          <KeyRound size={16} />
          Join with a code
        </button>
        <button type="button" className="primary" onClick={onCreateHospital}>
          <Plus size={16} />
          Create a hospital
        </button>
      </div>
    </div>
  );
}
