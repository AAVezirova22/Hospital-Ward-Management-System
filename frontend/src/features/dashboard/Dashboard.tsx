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
import {
  Users,
  BedDouble,
  Stethoscope,
  Activity,
  Sparkles,
  ArrowUpRight,
  ArrowRight,
} from "lucide-react";
import { OverviewHero, Reveal, MagneticButton } from "../../cinematic";
import { OperationsOverview } from "./OperationsOverview";
import { RoomCard } from "../rooms/RoomCard";
export function Dashboard({
  onAssistant = () => window.dispatchEvent(new Event("open-assistant")),
}: { onAssistant?: () => void } = {}) {
  const { data: d, error, isLoading } = useData("/reports/dashboard");
  const { data: rooms } = useData("/rooms");
  const { data: admissions } = useData("/admissions");
  return (
    <>
      <OverviewHero>
        <Title
          eyebrow="DEPARTMENT OVERVIEW"
          title="A clear picture of today."
          description="The people, capacity and activity that keep your department moving."
        >
          <span className="live-label">
            <span className="live-dot" />
            Live operations
          </span>
        </Title>
      </OverviewHero>
      <OperationsOverview />
      <ErrorBox error={error} />
      {isLoading ? (
        <div className="skeleton">Loading department state…</div>
      ) : (
        d && (
          <div className="metrics">
            {[
              [
                "Active admissions",
                d.activeAdmissions,
                "Assigned scope",
                Users,
              ],
              [
                "Beds occupied",
                `${d.occupiedBeds} / ${d.totalBeds}`,
                `${d.availableBeds} beds available`,
                BedDouble,
              ],
              [
                "Active doctors",
                d.activeDoctors,
                "Department team",
                Stethoscope,
              ],
              [
                "Procedures today",
                d.proceduresToday,
                "Recorded in UTC",
                Activity,
              ],
            ].map(([label, value, note, Icon]: any, index) => (
              <Reveal className="metric" key={label} delay={index * 0.08}>
                <div>
                  <span>{label}</span>
                  <Icon size={19} />
                </div>
                <strong>{value}</strong>
                <small>{note}</small>
              </Reveal>
            ))}
          </div>
        )
      )}
      <Reveal>
        <section className="brief">
          <div className="brief-icon">
            <Sparkles size={22} />
          </div>
          <div>
            <span className="eyebrow">OPERATIONS BRIEF</span>
            <p>
              {isLoading
                ? "Preparing your operational snapshot…"
                : d
                  ? `${d.availableBeds} beds are available across the department. ${d.activeAdmissions} active admissions are visible in your scope. ${d.proceduresToday} procedures have been recorded today.`
                  : "Department totals could not be loaded. Refresh the page to try again."}
            </p>
            <small>Live department totals</small>
          </div>
          <MagneticButton className="secondary" onClick={onAssistant}>
            Explore with assistant
          </MagneticButton>
        </section>
      </Reveal>
      <div className="overview-grid">
        <section className="panel">
          <div className="section-heading">
            <div>
              <span className="eyebrow">SPACE FOR CARE</span>
              <h2>Room capacity</h2>
            </div>
            <Link to="/app/rooms">
              View all
              <ArrowUpRight size={16} />
            </Link>
          </div>
          <div className="mini-rooms">
            {Array.isArray(rooms) &&
              rooms
                .slice(0, 8)
                .map((r: Row) => <RoomCard key={r.id} room={r} compact />)}
          </div>
          <div className="legend">
            <span>
              <i />
              Occupied
            </span>
            <span>
              <i />
              Available
            </span>
          </div>
        </section>
        <section className="panel">
          <div className="section-heading">
            <div>
              <span className="eyebrow">IN YOUR DEPARTMENT</span>
              <h2>Recent admissions</h2>
            </div>
            <Link to="/app/admissions">
              View all
              <ArrowUpRight size={16} />
            </Link>
          </div>
          <div className="recent-list">
            {Array.isArray(admissions) &&
              admissions.slice(0, 5).map((v: Row) => (
                <Link key={v.admission.id} to={"/app/patients/" + v.patient.id}>
                  <div className="avatar">
                    {v.patient.firstName[0]}
                    {v.patient.lastName[0]}
                  </div>
                  <div>
                    <strong>{fullName(v.patient)}</strong>
                    <small>
                      {v.patient.patientIdentifier} ·{" "}
                      {date(v.admission.admissionDateTime)}
                    </small>
                  </div>
                  <Status value={v.admission.status} />
                </Link>
              ))}
            {Array.isArray(admissions) && admissions.length === 0 && <Empty />}
          </div>
        </section>
      </div>
    </>
  );
}
