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
