"use client";
import { BedDouble, ArrowRight } from "lucide-react";
import type { AdmissionView, RoomCapacity } from "../../api/contracts";
import { projectRooms, type PlannedTransfer } from "./model";

export function WardMap({ rooms, admissions = [], plan = [], selected, onSelect, onDrop, changedRoom, warning = 75, critical = 90 }: {
  rooms: RoomCapacity[]; admissions?: AdmissionView[]; plan?: PlannedTransfer[];
  selected?: number; onSelect?: (id: number) => void; onDrop?: (admissionId: number, roomId: number) => void;
  changedRoom?: number; warning?: number; critical?: number;
}) {
  return <div className="ward-map" aria-label="Live ward capacity map">
    {projectRooms(rooms, plan).sort((a,b) => a.roomNumber.localeCompare(b.roomNumber, undefined, {numeric:true})).map(room => {
      const percent = room.projectedBeds / room.bedCount * 100;
      const state = !room.active ? "inactive" : percent >= critical ? "critical" : percent >= warning ? "warning" : "safe";
      const occupants = admissions.filter(a => a.admission.status === "ACTIVE" && a.assignment?.roomId === room.id);
      return <section key={room.id} className={`ward-room ${state} ${changedRoom === room.id ? "room-changed" : ""}`}
        onDragOver={onDrop && room.active ? e => { e.preventDefault(); e.dataTransfer.dropEffect = "move"; } : undefined}
        onDrop={onDrop ? e => { e.preventDefault(); const id = Number(e.dataTransfer.getData("application/medcore-admission")); if (id) onDrop(id, room.id); } : undefined}>
        <div className="ward-room-heading"><h3>Room {room.roomNumber}</h3><span>{room.active ? `${Math.round(percent)}%` : "Inactive"}</span></div>
        <div className="bed-slots" aria-label={`${room.projectedBeds} of ${room.bedCount} capacity slots occupied`}>
          {Array.from({length:room.bedCount}, (_,i) => <BedDouble key={i} aria-hidden="true" className={i < room.projectedBeds ? "occupied" : "vacant"} size={23}/>)}
        </div>
        <p>{room.projectedBeds} / {room.bedCount} occupied {room.projectedBeds !== room.occupiedBeds && <span className="projection">· projected</span>}</p>
        {occupants.map(v => <button type="button" className={`ward-patient ${selected === v.admission.id ? "selected" : ""}`} key={v.admission.id}
          disabled={!onSelect} draggable={Boolean(onDrop)} onClick={() => onSelect?.(v.admission.id)}
          onDragStart={e => { e.dataTransfer.setData("application/medcore-admission", String(v.admission.id)); e.dataTransfer.effectAllowed = "move"; onSelect?.(v.admission.id); }}>
          {v.patient.firstName} {v.patient.lastName}{plan.some(p => p.admissionId === v.admission.id) && <ArrowRight size={14}/>}
        </button>)}
      </section>;
    })}
    {!rooms.length && <p className="empty">No rooms have been configured.</p>}
  </div>;
}
