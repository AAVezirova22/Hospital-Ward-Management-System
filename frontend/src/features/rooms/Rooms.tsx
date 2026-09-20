"use client";
import React, { useState, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter, usePathname } from "next/navigation";
import type { RoomCapacity } from "../../api/contracts";
import {
  Link,
  useUser,
  useData,
  ErrorBox,
  Empty,
  Status,
  Modal,
  Title,
} from "../../components/workspace";
import { Plus, BedDouble } from "lucide-react";
import { motion } from "motion/react";
import { RoomCard } from "./RoomCard";
import { EntityForm } from "../administration/EntityForm";
import { useUrlState } from "../../components/useUrlState";
export function Rooms() {
  const [edit, setEdit] = useState<Partial<RoomCapacity> | null>(null),
    [free, setFree] = useState(false);
  const { data, error, isLoading } = useData<RoomCapacity[]>("/rooms");
  const user = useUser();
  const [selectedRoom, setSelectedRoom] = useUrlState("room");
  return (
    <>
      <Title
        eyebrow="CAPACITY MATRIX"
        title="The right space, in view."
        description="Live bed availability. Capacity is checked again at admission and transfer."
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
            onChange={(e) => setFree(e.target.checked)}
          />
          Available only
        </label>
      </div>
      <ErrorBox error={error} />
      {isLoading ? (
        <div className="skeleton">Loading capacity…</div>
      ) : (
        <motion.div layout className="room-grid">
          {Array.isArray(data) &&
            data
              .filter((r) => !free || r.availableBeds > 0)
              .filter((r) => !selectedRoom || String(r.id) === selectedRoom)
              .map((r) => (
                <motion.div layout key={r.id}>
                  <RoomCard
                    room={r}
                    onEdit={
                      user.role === "ADMIN" ? () => setEdit(r) : undefined
                    }
                  />
                </motion.div>
              ))}
        </motion.div>
      )}
      {edit && (
        <EntityForm kind="rooms" record={edit} onClose={() => setEdit(null)} />
      )}
    </>
  );
}
