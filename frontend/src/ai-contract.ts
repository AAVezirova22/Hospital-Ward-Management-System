import { z } from "zod";
export const safeRoute = z
  .string()
  .regex(
    /^\/app\/(dashboard|patients(?:\/[\w-]+)?|rooms|admissions|doctors|procedures|reports|users|planner|audit|presentation|care-pathways|tasks)$/,
  );
const patient = z
  .object({
    id: z.number().int().positive(),
    firstName: z.string(),
    lastName: z.string(),
    patientIdentifier: z.string(),
  })
  .passthrough();
const room = z
  .object({
    id: z.number().int().positive(),
    roomNumber: z.string(),
    availableBeds: z.number().nonnegative(),
    bedCount: z.number().positive(),
    capabilities: z.array(z.string()).default([]),
  })
  .passthrough();
const workflowCitation = z.object({
  sourceId: z.string().optional(),
  sourceName: z.string(),
  location: z.string(),
  reportedLocation: z.string().nullable().optional(),
  excerpt: z.string(),
  verified: z.boolean(),
  characterStart: z.number().int().optional(),
  characterEnd: z.number().int().optional(),
});
const workflowFieldEvidence = z.object({
  status: z.enum(["SUPPORTED", "UNCERTAIN", "CONFLICT", "UNRESOLVED"]),
  confidence: z.number().min(0).max(1),
  requiresDecision: z.boolean(),
  sources: z.array(workflowCitation),
  conflicts: z.array(workflowCitation),
});
const base = {
  message: z.string(),
  sessionId: z.string().nullable(),
  model: z.string().nullable(),
};
export const aiResponse = z.discriminatedUnion("responseType", [
  z.object({
    ...base,
    responseType: z.literal("FILE_REQUEST"),
    data: z.object({ ids: z.array(z.string().min(1).max(64)).min(1).max(10) }),
  }),
  z.object({
    ...base,
    responseType: z.literal("WORKFLOW_PROPOSAL"),
    data: z.object({
      action: z
        .object({
          id: z.number().int().positive(),
          actionType: z.literal("WORKFLOW"),
          expiresAt: z.string().datetime(),
          status: z.literal("PENDING"),
        })
        .passthrough(),
      workflow: z.object({
        title: z.string(),
        steps: z
          .array(
            z.object({
              key: z.string(),
              operation: z.string(),
              source: z.string(),
              fields: z.record(z.string(), z.unknown()),
              evidence: z.record(z.string(), workflowFieldEvidence).default({}),
            }),
          )
          .min(1)
          .max(50),
      }),
    }),
  }),
  z.object({
    ...base,
    responseType: z.literal("TEXT"),
    data: z.record(z.string(), z.unknown()),
  }),
  z.object({
    ...base,
    responseType: z.literal("ERROR"),
    data: z.record(z.string(), z.unknown()),
  }),
  z.object({
    ...base,
    responseType: z.literal("PATIENT_LIST"),
    data: z.object({ patients: z.array(patient) }),
  }),
  z.object({
    ...base,
    responseType: z.literal("PATIENT_SUMMARY"),
    data: z.object({
      patient,
      admissions: z.array(
        z
          .object({
            admission: z
              .object({
                id: z.number(),
                admissionNumber: z.string(),
                status: z.string(),
              })
              .passthrough(),
            doctor: z
              .object({ firstName: z.string(), lastName: z.string() })
              .passthrough(),
          })
          .passthrough(),
      ),
    }),
  }),
  z.object({
    ...base,
    responseType: z.literal("ROOM_LIST"),
    data: z.object({
      rooms: z.array(room),
      requiredCapabilities: z.array(z.string()).default([]),
      excludedRooms: z
        .array(
          z
            .object({
              id: z.number().int().positive(),
              roomNumber: z.string(),
              missingCapabilities: z.array(z.string()),
              reason: z.string(),
            })
            .passthrough(),
        )
        .default([]),
    }),
  }),
  z.object({
    ...base,
    responseType: z.literal("NAVIGATION_COMMAND"),
    data: z.object({ route: safeRoute }),
  }),
  z.object({
    ...base,
    responseType: z.literal("REPORT_RESULT"),
    data: z.record(z.string(), z.unknown()),
  }),
  z.object({
    ...base,
    responseType: z.literal("CONFIRMATION_CARD"),
    data: z
      .object({
        action: z
          .object({
            id: z.number().int().positive(),
            actionType: z.enum(["ADMISSION", "TRANSFER", "DISCHARGE"]),
            expiresAt: z.string().datetime(),
            status: z.literal("PENDING"),
          })
          .passthrough(),
        patient,
        requiredRoomCapabilities: z.array(z.string()).default([]),
      })
      .passthrough(),
  }),
]);
export type AiResponse = z.infer<typeof aiResponse>;
