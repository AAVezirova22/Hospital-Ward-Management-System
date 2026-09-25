"use client";

import type { CareTaskDefinition } from "./types";

type Assignee = {
  id: number;
  displayName: string;
  role: CareTaskDefinition["ownerRole"];
};

export function PatientTaskEditor({
  tasks,
  assignees,
  onChange,
}: {
  tasks: CareTaskDefinition[];
  assignees: Assignee[];
  onChange: (tasks: CareTaskDefinition[]) => void;
}) {
  function update(index: number, patch: Partial<CareTaskDefinition>) {
    onChange(
      tasks.map((task, position) =>
        position === index ? { ...task, ...patch } : task,
      ),
    );
  }

  return (
    <div className="care-patient-task-editor">
      <h3>Tailor the published tasks</h3>
      <p>Changes apply to this patient only. Review the resulting timeline before launch.</p>
      {tasks.map((task, index) => (
        <div className="care-task-editor" key={task.key}>
          <div className="section-heading">
            <strong>Step {index + 1}</strong>
            <span className="status">Patient plan</span>
          </div>
          <div className="care-task-fields">
            <label>
              Task title
              <input
                value={task.title}
                maxLength={200}
                onChange={(event) => update(index, { title: event.target.value })}
              />
            </label>
            <label>
              Owner role
              <select
                value={task.ownerRole}
                onChange={(event) =>
                  update(index, {
                    ownerRole: event.target.value as CareTaskDefinition["ownerRole"],
                    assignedUserId: null,
                  })
                }
              >
                <option value="DOCTOR">Doctor</option>
                <option value="MEDICAL_STAFF">Medical staff</option>
                <option value="ADMIN">Department admin</option>
              </select>
            </label>
            <label>
              Assign to
              <select
                value={task.assignedUserId ?? ""}
                onChange={(event) =>
                  update(index, {
                    assignedUserId: event.target.value
                      ? Number(event.target.value)
                      : null,
                  })
                }
              >
                <option value="">Unassigned until care team assigns</option>
                {assignees
                  .filter((person) => person.role === task.ownerRole)
                  .map((person) => (
                    <option key={person.id} value={person.id}>
                      {person.displayName}
                    </option>
                  ))}
              </select>
            </label>
            <label>
              Due after launch, minutes
              <input
                type="number"
                min="0"
                max="525600"
                value={task.dueOffsetMinutes}
                onChange={(event) =>
                  update(index, { dueOffsetMinutes: Number(event.target.value) })
                }
              />
            </label>
          </div>
          <fieldset className="care-dependency-choices">
            <legend>Depends on</legend>
            {index === 0 && <span>No earlier task</span>}
            {tasks.slice(0, index).map((preceding, position) => (
              <label key={preceding.key}>
                <input
                  type="checkbox"
                  checked={task.dependsOn.includes(preceding.key)}
                  onChange={(event) =>
                    update(index, {
                      dependsOn: event.target.checked
                        ? [...task.dependsOn, preceding.key]
                        : task.dependsOn.filter((key) => key !== preceding.key),
                    })
                  }
                />
                {preceding.title || `Step ${position + 1}`}
              </label>
            ))}
          </fieldset>
          <label>
            Instructions
            <textarea
              value={task.description}
              maxLength={2000}
              onChange={(event) =>
                update(index, { description: event.target.value })
              }
            />
          </label>
        </div>
      ))}
    </div>
  );
}
