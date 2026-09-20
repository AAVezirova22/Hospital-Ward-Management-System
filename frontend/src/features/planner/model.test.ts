import {describe,it,expect} from "vitest";
import {executableOrder,projectRooms,validateTransfer,type PlannedTransfer} from "./model";
import type {RoomCapacity,AdmissionView} from "../../api/contracts";
const room=(id:number,occupiedBeds:number,bedCount=2,active=true)=>({id,occupiedBeds,bedCount,active,roomNumber:`Ward ${id}`,availableBeds:bedCount-occupiedBeds}) as RoomCapacity;
const transfer=(id:number,fromRoomId:number,toRoomId:number)=>({admissionId:id,fromRoomId,toRoomId,version:1,patientName:`Patient ${id}`}) satisfies PlannedTransfer;
describe("ward planning",()=>{
 it("projects multiple transfers without modifying saved capacity",()=>{const rooms=[room(1,2),room(2,0)];expect(projectRooms(rooms,[transfer(1,1,2),transfer(2,1,2)]).map(r=>r.projectedBeds)).toEqual([0,2]);expect(rooms[0].occupiedBeds).toBe(2);});
 it("orders dependent transfers so a bed is actually available at each confirmation",()=>{const a=transfer(1,1,2),b=transfer(2,2,3);expect(executableOrder([room(1,2),room(2,2),room(3,1)],[a,b])).toEqual([b,a]);});
 it("rejects full-room cycles and deactivated destinations",()=>{expect(executableOrder([room(1,2),room(2,2)],[transfer(1,1,2),transfer(2,2,1)])).toBeNull();expect(executableOrder([room(1,1),room(2,0,2,false)],[transfer(1,1,2)])).toBeNull();});
 it("replacing a staged destination does not count the same patient twice",()=>{const view={admission:{id:1,status:"ACTIVE"},assignment:{roomId:1}} as AdmissionView;const rooms=[room(1,2),room(2,1)];expect(validateTransfer(view,rooms[1],rooms,[transfer(1,1,2)])).toBeNull();expect(validateTransfer(view,room(2,2),[rooms[0],room(2,2)],[])).toMatch(/capacity/);});
 it("blocks missing inactive and same-room placements",()=>{const view={admission:{id:1,status:"ACTIVE"},assignment:{roomId:1}} as AdmissionView;expect(validateTransfer(undefined,room(2,0),[],[])).toMatch(/active admission/);expect(validateTransfer(view,room(2,0,2,false),[],[])).toMatch(/inactive/);expect(validateTransfer(view,room(1,1),[],[])).toMatch(/already/);});
});
