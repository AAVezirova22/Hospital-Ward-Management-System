export type CareTaskDefinition = {
  key: string;
  title: string;
  description: string;
  ownerRole: "DOCTOR" | "MEDICAL_STAFF" | "ADMIN";
  assignedUserId: number | null;
  dueOffsetMinutes: number;
  dependsOn: string[];
};
export type CareTemplateDefinition = {
  triggers: ("MANUAL" | "ADMISSION" | "DISCHARGE")[];
  tasks: CareTaskDefinition[];
};
export type CareTemplateSummary = {
  id: number;
  name: string;
  description: string;
  version: number;
  published_version: number | null;
  created_by: number;
  created_at: string;
  updated_at: string;
};
export type CareTemplate = CareTemplateSummary & {
  draft: CareTemplateDefinition;
  publishedVersions: {
    id: number;
    version: number;
    definition: CareTemplateDefinition;
    published_by: number;
    published_at: string;
  }[];
};
export type CareTask = CareTaskDefinition & {
  id: number;
  patientId: number;
  workflowRunId: number;
  dueAt: string | null;
  dueOn: string | null;
  dueTime: string | null;
  status: "OPEN" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  dependencyState: string;
  taskOrigin: string;
  sourceReference: string | null;
  sourceExcerpt: string | null;
  sourceLocation: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
};
export type CarePreview = {
  templateId: number;
  workflowVersion: number;
  patientId: number;
  admissionId: number | null;
  trigger: string;
  baseTime: string;
  portalSummaryConsentActive: boolean;
  tasks: (CareTaskDefinition & {
    dueAt: string | null;
    dueOn?: string | null;
    dueTime?: string | null;
    taskOrigin?: string;
    sourceName?: string | null;
    sourceExcerpt?: string | null;
    sourceLocation?: string | null;
    dependencyState: string;
  })[];
};
export type CareRun = {
  id: number;
  templateId: number;
  workflowVersionId: number;
  workflowVersion: number;
  patientId: number;
  admissionId: number | null;
  trigger: string;
  status: string;
  version: number;
  sourceReference: string | null;
  patientSummary: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  launchedBy: number | null;
  launchedAt: string | null;
  cancelledAt: string | null;
  tasks: CareTask[];
};
