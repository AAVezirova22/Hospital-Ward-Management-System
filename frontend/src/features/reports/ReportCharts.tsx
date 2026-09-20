"use client";
import type {ProcedureReport,RoomCapacity} from "../../api/contracts";
import {money} from "../../api";
export function ProcedureCharts({report,onDoctor}:{report:ProcedureReport;onDoctor:(id:string)=>void}) {
  const doctors=Object.entries(report.byDoctor),max=Math.max(1,...doctors.map(([,v])=>v));
  const types=new Map<string,number>();report.rows.forEach(r=>types.set(r.procedure.procedureName,(types.get(r.procedure.procedureName)??0)+1));
  return <div className="operations-bottom"><section className="panel report-bars"><h2>Procedure cost by doctor</h2><p>Select a doctor to filter the records below.</p>{doctors.map(([id,total])=><button key={id} onClick={()=>onDoctor(id)}>{report.rows.find(r=>String(r.doctor.id)===id)?.doctor.lastName}<strong>{money(total)}</strong><meter value={total} max={max} aria-label={`Doctor total ${money(total)}`}/></button>)}</section><section className="panel report-bars"><h2>Procedures by type</h2>{Array.from(types.entries()).map(([name,count])=><div key={name}>{name}<strong>{count}</strong><meter value={count} max={Math.max(1,report.rows.length)} aria-label={`${name}: ${count}`}/></div>)}</section></div>;
}
export function CapacityChart({rooms,onRoom}:{rooms:RoomCapacity[];onRoom:(id:string)=>void}) {
  return <section className="panel report-bars"><h2>Occupancy by room</h2><p>Select a room to inspect its current patients.</p>{rooms.map(r=><button key={r.id} onClick={()=>onRoom(String(r.id))}>Room {r.roomNumber}{!r.active && " · inactive"}<strong>{r.occupiedBeds} / {r.bedCount}</strong><meter value={r.occupiedBeds} max={r.bedCount} aria-label={`Room ${r.roomNumber} occupancy`}/></button>)}</section>;
}
