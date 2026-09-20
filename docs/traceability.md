# Plan traceability

The original 76-section plan is retained in `plan.md`. This table maps every section to implementation or an explicitly optional design choice.

| Plan sections | Implementation / evidence |
| --- | --- |
| 1–5 Objective, principles, architecture, stack, repository | Full-stack source, README, Compose; Java 21, Next.js/TypeScript/Motion, Spring Boot/Tomcat/JPA/Security, PostgreSQL/Flyway; backend authoritative |
| 6–17 Domain records | Twelve JPA records plus workflow lock; V1 migration, explicit FKs, optimistic versions, partial indexes, audit/AI metadata/proposals |
| 18–23 AI orchestration and write boundary | AiModelClient, AiToolRegistry, AiAssistantService, AiActionService; read/prepare allow-list; no confirm tool |
| 24–26 APIs and structured output | AiController, typed Response, frontend Zod envelope and result components |
| 27–30 Context, authorization, injection, validation | Role/route/selected ID context; dynamic tool definitions, service checks, strict tool argument validation; notes displayed as data |
| 31–33 Failures, timeout and limits | Structured fallback, external HTTP timeout, per-user quota and one in-flight request |
| 34–38 Search, briefing, reports, history, navigation | Local documented command patterns plus external tool adapter; authoritative report service; dossier history and allow-listed navigation |
| 39–42 Assistant UX and workflow preparation | Ctrl/Cmd+K drawer, result cards, proposal review/confirmation; conventional form when details are missing |
| 43–46 Concurrency, services, REST, errors | Transactional workflow row lock, occupancy recheck, ordinary API controllers and standard error envelope |
| 47–49 Frontend integration and state | React/Motion views and TanStack Query; mutation invalidation; no cached server state in localStorage |
| 50–53 Security and minimization | Session/CSRF/RBAC, password hashing, per-request account revalidation, minimal model context, metadata-only audit |
| 54–62 Tests and evaluations | HospitalIntegrationTest, AiModelTest, AiSafetyTest and Playwright E2E workflows |
| 63 Performance | Metrics and assistant load independently; core APIs do not wait on a model; no report depends on AI |
| 64 Streaming | Optional in plan; deliberately not enabled. Whole structured responses are validated before rendering. |
| 65 Caching | Short-lived user-scoped TanStack Query data; cleared at logout; server rechecks room capacity. No shared patient cache. |
| 66–67 Motion and accessibility | Route/dossier transitions, capacity layout animation, login reveal, authoritative post-save feedback; native dialogs and reduced-motion support. Decorative scans and moving grids are not used. |
| 68–70 Dashboard, commands, fallback | Department overview, independent brief, command drawer and conventional equivalent for each operation |
| 71 Reports | Census, room/doctor filters, capacity, procedures by patient/period, grouped and overall historical totals, CSV |
| 72 Roadmap | All functional phases represented in source; tests and development/deployment configuration included |
| 73 Demonstration | docs/demo.md plus browser workflow suite |
| 74 Definition of done | Schema, models, services, policies, validators, endpoints, views, loading/errors, tests and documentation supplied |
| 75–76 Priority and boundaries | Core operations independent of AI; model never owns data, permissions or critical confirmation |

The plan proposes provider examples rather than requiring one named external service. The default local interpreter is intentionally limited and labeled. The external adapter is implemented and contract-tested; a live provider was not selected or credentialed in this task. Optional report-export/procedure-entry preparation examples are performed through the conventional report/procedure UI; the roadmap's three required AI preparations (admission, transfer, discharge) are implemented.

The app uses semantic inline capacity indicators rather than a decorative moving floor plan. Actual transfer state changes only after successful server confirmation. Production deployment and native-database load certification are separate from source implementation; consult verification.md for the executed validation environment.
