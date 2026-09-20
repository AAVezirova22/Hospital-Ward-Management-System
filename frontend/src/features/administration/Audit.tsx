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
export function Audit() {
  const { data, error, isLoading } = useData("/audit");
  return (
    <>
      <Title
        eyebrow="ACCOUNTABILITY"
        title="A trace of every change."
        description="The latest 100 audit events. Patient notes and passwords are excluded."
      />
      <ErrorBox error={error} />
      <section className="panel table-panel">
        <table>
          <thead>
            <tr>
              <th scope="col">Timestamp</th>
              <th scope="col">Event</th>
              <th scope="col">Entity</th>
              <th scope="col">User ID</th>
              <th scope="col">Source</th>
            </tr>
          </thead>
          <tbody>
            {Array.isArray(data) &&
              data.map((a: Row) => (
                <tr key={a.id}>
                  <td>{date(a.timestamp)}</td>
                  <td className="mono">{a.eventType}</td>
                  <td>
                    {a.entityType} #{a.entityId}
                  </td>
                  <td>{a.userId}</td>
                  <td>{a.source}</td>
                </tr>
              ))}
          </tbody>
        </table>
        {isLoading && <div className="skeleton">Loading audit trail…</div>}
      </section>
    </>
  );
}
