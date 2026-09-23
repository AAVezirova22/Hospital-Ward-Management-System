"use client";
import React, { useState, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter, usePathname } from "next/navigation";
import { api, fullName, money, date, type Row, type User } from "../../api";
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
import { Plus, BedDouble } from "../../icons";
import { motion } from "motion/react";
import { RoomCard } from "./RoomCard";
import { EntityForm } from "../administration/EntityForm";
import { useUrlState } from "../../components/useUrlState";
export function Rooms() {
  const [edit, setEdit] = useState<Row | null>(null),
    [free, setFree] = useState(false);
  const user = useUser();
  const [selectedRoom, setSelectedRoom] = useUrlState("room"),
    [search, setSearch] = useUrlState("q"),
    [pageText, setPageText] = useUrlState("page", "0");
  const page = Math.max(0, Number.parseInt(pageText, 10) || 0);
  const params = new URLSearchParams({ page: String(page) });
  if (search) params.set("q", search);
  if (selectedRoom) params.set("roomId", selectedRoom);
  if (free) params.set("minFree", "1");
  const { data, error, isLoading } = useData(`/rooms?${params}`);
  return (
    <>
      <Title
        eyebrow="Capacity matrix"
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
          <button
            className="text-button"
            onClick={() => {
              setSelectedRoom("");
              setPageText("0");
            }}
          >
            Show all rooms
          </button>
        )}
        <label>
          Search rooms
          <input
            value={search}
            onChange={(e) => {
              setPageText("0");
              setSearch(e.target.value);
            }}
            placeholder="Room number"
          />
        </label>
        <label className="inline-check">
          <input
            type="checkbox"
            checked={free}
            onChange={(e) => {
              setPageText("0");
              setFree(e.target.checked);
            }}
          />
          Available only
        </label>
      </div>
      <ErrorBox error={error} />
      {isLoading ? (
        <div className="skeleton">Loading capacity…</div>
      ) : (
        <motion.div layout className="room-grid">
          {Array.isArray(data?.items) &&
            data.items.map((r: Row) => (
              <motion.div layout key={r.id}>
                <RoomCard
                  room={r}
                  onEdit={user.role === "ADMIN" ? () => setEdit(r) : undefined}
                />
              </motion.div>
            ))}
        </motion.div>
      )}
      {data && data.totalPages > 0 && (
        <div className="toolbar" role="navigation" aria-label="Room pages">
          <button
            className="secondary"
            disabled={data.page === 0}
            onClick={() => setPageText(String(data.page - 1))}
          >
            Previous
          </button>
          <span>
            Page {data.page + 1} of {data.totalPages} · {data.totalElements}{" "}
            rooms
          </span>
          <button
            className="secondary"
            disabled={!data.hasNext}
            onClick={() => setPageText(String(data.nextPage))}
          >
            Next
          </button>
        </div>
      )}
      {data && !data.items.length && <Empty />}
      {edit && (
        <EntityForm kind="rooms" record={edit} onClose={() => setEdit(null)} />
      )}
    </>
  );
}
