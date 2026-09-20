"use client";
import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BedDouble, Building2, ChevronDown, Copy, KeyRound, Plus } from "lucide-react";
import {
  api,
  activeDepartment,
  setActiveDepartment,
} from "../api";
import type { WorkspaceHospital, WorkspaceList } from "../api/contracts";
import { ErrorBox, Modal } from "./workspace";

function currentNames(data: WorkspaceList | undefined) {
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

async function copyCode(value: string) {
  try {
    await navigator.clipboard.writeText(value);
    window.dispatchEvent(
      new CustomEvent("saved", { detail: "Join code copied" }),
    );
  } catch {}
}
