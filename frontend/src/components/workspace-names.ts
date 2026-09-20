import { activeDepartment } from "../api";
import type { WorkspaceList } from "../api/contracts";

export function currentNames(data: WorkspaceList | undefined) {
  const id = Number(activeDepartment() || data?.activeDepartmentId || 0);
  for (const hospital of data?.hospitals ?? []) {
    const department = hospital.departments.find((item) => item.id === id);
    if (department) return { hospital, department };
  }
  return {
    hospital: data?.hospitals[0],
    department: data?.hospitals[0]?.departments[0],
  };
}
