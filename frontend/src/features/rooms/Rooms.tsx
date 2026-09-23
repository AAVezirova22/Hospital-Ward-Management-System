"use client";
import { useState } from "react";
import { motion } from "motion/react";
import { type Row } from "../../api";
import type { RoomCapacity } from "../../api/contracts";
import { useUser, useData, ErrorBox, Title } from "../../components/workspace";
import { Plus, BedDouble } from "../../icons";
import { RoomCard } from "./RoomCard";
import { BedHoldManager } from "./BedHoldManager";
import { EntityForm } from "../administration/EntityForm";
import { useUrlState } from "../../components/useUrlState";

export function Rooms() {
  const [edit, setEdit] = useState<Row | null>(null);
  const [holdRoom, setHoldRoom] = useState<Row | null>(null);
  const [free, setFree] = useState(false);
  const { data, error, isLoading } = useData("/rooms");
  const user = useUser();
  const [selectedRoom, setSelectedRoom] = useUrlState("room");
  const canManageHolds = user.role === "ADMIN" || user.role === "MEDICAL_STAFF";
  const selectedHoldRoom = Array.isArray(data) && holdRoom
    ? data.find((room: Row) => room.id === holdRoom.id) ?? holdRoom
    : holdRoom;

  return (
    <>
      <Title
        eyebrow="Capacity matrix"
        title="The right space, in view."
        description="Live bed availability includes current and upcoming maintenance holds. Capacity is checked again at admission and transfer."
      >
        {user.role === "ADMIN" && (
          <button className="primary" onClick={() => setEdit({})}>
            <Plus size={18} />
            Add room
          </button>
        )}
      </Title>
      <div className="toolbar">
        <BedDouble size={19} />
        <span>Department rooms</span>
        {selectedRoom && (
          <button className="text-button" onClick={() => setSelectedRoom("")}>
            Show all rooms
          </button>
        )}
        <label className="inline-check">
          <input
            type="checkbox"
            checked={free}
            onChange={(event) => setFree(event.target.checked)}
          />
          Available only
        </label>
      </div>
      <ErrorBox error={error} />
      {isLoading ? (
        <div className="skeleton">Loading capacity...</div>
      ) : (
        <motion.div layout className="room-grid">
          {Array.isArray(data) &&
            data
              .filter((room: Row) => !free || room.availableBeds > 0)
              .filter(
                (room: Row) => !selectedRoom || String(room.id) === selectedRoom,
              )
              .map((room: Row) => (
                <motion.div layout key={room.id}>
                  <RoomCard
                    room={room}
                    onEdit={user.role === "ADMIN" ? () => setEdit(room) : undefined}
                    onManageHolds={canManageHolds ? () => setHoldRoom(room) : undefined}
                  />
                </motion.div>
              ))}
        </motion.div>
      )}
      {edit && (
        <EntityForm kind="rooms" record={edit} onClose={() => setEdit(null)} />
      )}
      {selectedHoldRoom && (
        <BedHoldManager
          room={selectedHoldRoom as RoomCapacity}
          onClose={() => setHoldRoom(null)}
        />
      )}
    </>
  );
}
