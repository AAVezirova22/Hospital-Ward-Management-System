import type { AdmissionView } from "../../api/contracts";

export interface CareEvent {
  id: string;
  at: string;
  title: string;
  detail: string;
  kind: "admission" | "transfer" | "procedure" | "discharge";
  admissionNumber: string;
}
export function careTimeline(admissions: AdmissionView[]): CareEvent[] {
  return admissions
    .flatMap((v) => {
      const rooms = [...v.rooms].sort(
        (a, b) =>
          Date.parse(a.assignment.assignedAt) -
          Date.parse(b.assignment.assignedAt),
      );
      const events: CareEvent[] = [
        {
          id: `admission-${v.admission.id}`,
          at: v.admission.admissionDateTime,
          title: "Admitted",
          detail: rooms[0]
            ? `Room ${rooms[0].room.roomNumber}`
            : "Initial room not recorded",
          kind: "admission",
          admissionNumber: v.admission.admissionNumber,
        },
      ];
      rooms
        .slice(1)
        .forEach((r, i) =>
          events.push({
            id: `transfer-${r.assignment.id}`,
            at: r.assignment.assignedAt,
            title: "Room transfer",
            detail: `Room ${rooms[i].room.roomNumber} → ${r.room.roomNumber}${r.assignment.reason ? ` · ${r.assignment.reason}` : ""}`,
            kind: "transfer",
            admissionNumber: v.admission.admissionNumber,
          }),
        );
      v.procedures.forEach((p) =>
        events.push({
          id: `procedure-${p.record.id}`,
          at: p.record.performedAt,
          title: p.procedure.procedureName,
          detail: `Dr. ${p.doctor.firstName} ${p.doctor.lastName}${p.record.note ? ` · ${p.record.note}` : ""}`,
          kind: "procedure",
          admissionNumber: v.admission.admissionNumber,
        }),
      );
      if (v.admission.dischargeDateTime)
        events.push({
          id: `discharge-${v.admission.id}`,
          at: v.admission.dischargeDateTime,
          title: "Discharged",
          detail: "Stay completed",
          kind: "discharge",
          admissionNumber: v.admission.admissionNumber,
        });
      return events;
    })
    .sort(
      (a, b) => Date.parse(a.at) - Date.parse(b.at) || a.id.localeCompare(b.id),
    );
}
