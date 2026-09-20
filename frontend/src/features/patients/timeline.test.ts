import { describe, expect, it } from "vitest";
import { careTimeline } from "./timeline";
import type { AdmissionView } from "../../api/contracts";

describe("care journey", () => {
  it("merges unsorted room movements and procedures across stays without inventing historical doctors", () => {
    const stay = {
      admission: {
        id: 1,
        admissionNumber: "ADM-1",
        admissionDateTime: "2026-09-18T08:00:00Z",
        dischargeDateTime: "2026-09-20T08:00:00Z",
      },
      rooms: [
        {
          assignment: {
            id: 2,
            assignedAt: "2026-09-19T08:00:00Z",
            reason: "Capacity",
          },
          room: { roomNumber: "307" },
        },
        {
          assignment: { id: 1, assignedAt: "2026-09-18T08:00:00Z" },
          room: { roomNumber: "302" },
        },
      ],
      procedures: [
        {
          record: {
            id: 9,
            performedAt: "2026-09-18T18:00:00Z",
            note: "Completed",
          },
          procedure: { procedureName: "Blood panel" },
          doctor: { firstName: "Elena", lastName: "Dimitrova" },
        },
      ],
    } as AdmissionView;
    const result = careTimeline([stay]);
    expect(result.map((e) => e.title)).toEqual([
      "Admitted",
      "Blood panel",
      "Room transfer",
      "Discharged",
    ]);
    expect(result[2].detail).toBe("Room 302 → 307 · Capacity");
    expect(result[1].detail).toBe("Dr. Elena Dimitrova · Completed");
    expect(stay.rooms[0].room.roomNumber).toBe("307");
  });
  it("handles an admission with no placement history", () => {
    const result = careTimeline([
      {
        admission: {
          id: 1,
          admissionNumber: "ADM-1",
          admissionDateTime: "2026-09-18T08:00:00Z",
          dischargeDateTime: null,
        },
        rooms: [],
        procedures: [],
      } as unknown as AdmissionView,
    ]);
    expect(result).toHaveLength(1);
    expect(result[0].detail).toBe("Initial room not recorded");
    expect(careTimeline([])).toEqual([]);
  });
});
