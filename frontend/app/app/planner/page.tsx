"use client";
import { useUser } from "../../../src/components/workspace";
import { WardPlanner } from "../../../src/features/planner/WardPlanner";
export default function Page() {
  return <WardPlanner user={useUser()} />;
}
