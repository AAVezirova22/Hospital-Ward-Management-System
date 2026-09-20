"use client";

import type { WorkspaceHospital } from "../api/contracts";

export function JoinForm({
  joinCode,
  joinHint,
  busy,
  onCode,
  onBack,
  onJoin,
}: {
  joinCode: string;
  joinHint: string;
  busy: boolean;
  onCode: (value: string) => void;
  onBack: () => void;
  onJoin: () => void;
}) {
  return (
    <form
      className="workspace-form"
      onSubmit={(e) => {
        e.preventDefault();
        onJoin();
      }}
    >
      <p>
        A hospital code adds hospital membership. A department code opens that
        department as medical staff, never as an administrator.
      </p>
      {joinHint && (
        <p className="muted" role="status">
          {joinHint}
        </p>
      )}
      <label>
        Join code
        <input
          autoFocus
          value={joinCode}
          onChange={(e) => onCode(e.target.value)}
          placeholder="H- or D- code"
          autoComplete="off"
        />
      </label>
      <div className="actions">
        <button type="button" className="secondary" onClick={onBack}>
          Back
        </button>
        <button className="primary" disabled={busy || !joinCode.trim()}>
          {busy ? "Joining…" : "Join"}
        </button>
      </div>
    </form>
  );
}

export function CreateHospitalForm({
  hospitalName,
  departmentName,
  busy,
  onHospitalName,
  onDepartmentName,
  onBack,
  onCreate,
}: {
  hospitalName: string;
  departmentName: string;
  busy: boolean;
  onHospitalName: (value: string) => void;
  onDepartmentName: (value: string) => void;
  onBack: () => void;
  onCreate: () => void;
}) {
  return (
    <form
      className="workspace-form"
      onSubmit={(e) => {
        e.preventDefault();
        onCreate();
      }}
    >
      <p>
        You become the owner of the hospital and administrator of its first
        department.
      </p>
      <label>
        Hospital name
        <input
          autoFocus
          value={hospitalName}
          onChange={(e) => onHospitalName(e.target.value)}
          maxLength={120}
        />
      </label>
      <label>
        First department
        <input
          value={departmentName}
          onChange={(e) => onDepartmentName(e.target.value)}
          maxLength={120}
        />
      </label>
      <div className="actions">
        <button type="button" className="secondary" onClick={onBack}>
          Back
        </button>
        <button
          className="primary"
          disabled={busy || !hospitalName.trim() || !departmentName.trim()}
        >
          {busy ? "Creating…" : "Create hospital"}
        </button>
      </div>
    </form>
  );
}

export function CreateDepartmentForm({
  hostHospital,
  departmentName,
  busy,
  onDepartmentName,
  onBack,
  onCreate,
}: {
  hostHospital: WorkspaceHospital;
  departmentName: string;
  busy: boolean;
  onDepartmentName: (value: string) => void;
  onBack: () => void;
  onCreate: () => void;
}) {
  return (
    <form
      className="workspace-form"
      onSubmit={(e) => {
        e.preventDefault();
        onCreate();
      }}
    >
      <p>
        This department is added to {hostHospital.name}. You administer it.
      </p>
      <label>
        Department name
        <input
          autoFocus
          value={departmentName}
          onChange={(e) => onDepartmentName(e.target.value)}
          maxLength={120}
        />
      </label>
      <div className="actions">
        <button type="button" className="secondary" onClick={onBack}>
          Back
        </button>
        <button className="primary" disabled={busy || !departmentName.trim()}>
          {busy ? "Creating…" : "Create department"}
        </button>
      </div>
    </form>
  );
}
