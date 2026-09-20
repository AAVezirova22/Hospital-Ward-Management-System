"use client";
import { OverviewHero } from "../../cinematic";
import { Title } from "../../components/workspace";
import { OperationsOverview } from "./OperationsOverview";
import { HospitalSimulation } from "../demo/HospitalSimulation";
export function Dashboard({
  onAssistant = () => window.dispatchEvent(new Event("open-assistant")),
}: { onAssistant?: () => void } = {}) {
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
    </>
  );
}
