import { date } from "../../api";
import type { AdmissionView } from "../../api/contracts";

type RoomHistory = AdmissionView["rooms"];

export function RoomMovementHistory({ rooms }: { rooms: RoomHistory }) {
  const movements = [...rooms].sort(
    (a, b) =>
      new Date(a.assignment.assignedAt).getTime() -
      new Date(b.assignment.assignedAt).getTime(),
  );

  return (
    <section aria-label="Room history">
      <h3>Room history</h3>
      {movements.length ? (
        movements.map(({ assignment, room }) => (
          <div className="result-row" key={assignment.id}>
            <span>
              Room {room.roomNumber}{assignment.bedIdentifier ? ` · Bed ${assignment.bedIdentifier}` : ""}
              <small>
                Assigned {date(assignment.assignedAt)}
                {assignment.releasedAt
                  ? ` · Released ${date(assignment.releasedAt)}`
                  : " · Current assignment"}
              </small>
            </span>
          </div>
        ))
      ) : (
        <p>No room assignments recorded.</p>
      )}
    </section>
  );
}
