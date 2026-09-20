"use client";
import { OverviewHero } from "../../cinematic";
import { Title, useUser } from "../../components/workspace";
import { SystemHealth } from "../../components/SystemHealth";
import { OperationsOverview } from "./OperationsOverview";
import { HospitalSimulation } from "../demo/HospitalSimulation";
export function Dashboard({
  onAssistant = () => window.dispatchEvent(new Event("open-assistant")),
}: { onAssistant?: () => void } = {}) {
  const user = useUser();
  return (
    <>
      <OverviewHero>
        <Title
          eyebrow="WARD OPERATIONS"
          title="Your ward. In the moment."
          description="Capacity, care journeys and the next action, together."
        >
          <button className="secondary" onClick={onAssistant}>
            Operations assistant
          </button>
        </Title>
      </OverviewHero>
      <OperationsOverview />
      <HospitalSimulation />
      {user.role === "ADMIN" && <SystemHealth />}
    </>
  );
}
