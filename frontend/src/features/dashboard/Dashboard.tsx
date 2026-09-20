"use client";
import { useQuery } from "@tanstack/react-query";
import { OverviewHero } from "../../cinematic";
import { Title, useUser } from "../../components/workspace";
import { WorkspaceSwitcher } from "../../components/WorkspaceSwitcher";
import { SystemHealth } from "../../components/SystemHealth";
import { OperationsOverview } from "./OperationsOverview";
import { HospitalSimulation } from "../demo/HospitalSimulation";
import { api, activeDepartment } from "../../api";
import type { WorkspaceList } from "../../api/contracts";
export function Dashboard({
  onAssistant = () => window.dispatchEvent(new Event("open-assistant")),
}: { onAssistant?: () => void } = {}) {
  const user = useUser();
  const workspaces = useQuery<WorkspaceList>({
    queryKey: ["/workspaces", activeDepartment()],
    queryFn: () => api("/workspaces"),
  });
  const departments =
    workspaces.data?.hospitals.flatMap((hospital) => hospital.departments) ??
    [];
  if (workspaces.isSuccess && departments.length === 0) {
    return (
      <>
        <OverviewHero>
          <Title
            eyebrow="WORKSPACE"
            title="Create a hospital or join with a code."
            description="This account has no open department yet, so the ward looks empty until you join one."
          />
        </OverviewHero>
        <WorkspaceSwitcher />
      </>
    );
  }
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