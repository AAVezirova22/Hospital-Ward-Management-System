# Assistant files and workflows

## Use

Configure the existing external model adapter with `AI_MODE=external`, `AI_URL`, `AI_MODEL` and your provider's `AI_API_KEY` if required. Open Operations assistant, upload files or connect a folder, and describe the desired result. Examples:

- "Find the room inventory in this folder and prepare the rooms."
- "Create a hospital from these setup documents, including its patients and doctors."
- "Use this spreadsheet to prepare admissions; ask me if required details are missing."

The agent reads relevant connected files, queries authorized records to resolve references, and proposes supported operations. Review every field and source, then confirm or cancel. There is no autonomous confirmation tool. One confirmation applies the entire workflow transaction; errors roll back all its steps, including a new hospital. A successful new-hospital workflow opens its department.

The supported operations are hospital plus initial department creation; patient, doctor, room and procedure catalogue creation; admission, transfer, discharge and performed-procedure recording. Existing department permissions apply. New records can be referenced by subsequent steps. This composes the app's existing operations; it does not add arbitrary application integrations, custom executable workflows, shell execution or clinical decision-making.

## Existing tools reused

- The browser's [File System Access API](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access) supplies read-only directory handles. There is no new desktop daemon or custom filesystem protocol.
- [Apache Tika 3.3.2](https://tika.apache.org/3.3.2/) extracts document text, using its maintained PDF and Office parsers. No custom PDF, Word or spreadsheet parser was built.
- The existing OpenAI-compatible provider adapter, API client, Spring validation, business services, department isolation and expiring confirmation records handle planning and execution.

A connected folder grants access to that selected directory tree, not the whole computer. The directory picker needs a supported browser and a secure context (HTTPS or localhost). A folder-input fallback supports browsers without that picker. Closing or clearing the assistant drops its handles; reconnect to refresh a folder or read updated files. Permission denial and unreadable files are surfaced as errors.

File names from the connected folder and extracted text from attached/requested files are shared with the configured model when you send a message. Merely connecting a folder enumerates names without reading its file contents. Requested files are uploaded through the same authenticated, CSRF-protected API as manual uploads.

## Limits and retention

| Boundary | Limit |
| --- | --- |
| Supported extensions | txt, md, csv, tsv, json, pdf, docx, xlsx, pptx, odt, ods, rtf |
| File size | Nonempty, at most 5 MB |
| Extracted text | 40,000 characters per file; oversized extraction is rejected, not silently truncated |
| Request context | 20 attached sources, 120,000 extracted characters total |
| Connected folder | 300 supported files; native enumeration visits at most 3,000 entries and eight nested directory levels |
| Agent reads | At most ten requested files per response; at most three browser read/resubmit rounds |
| Model work | At most eight sequential tools per backend request; 15-second timeout per provider call |
| Workflow | At most 50 ordered steps and 50,000 characters of plan JSON |
| Source availability | 30 minutes, in process memory, owned by user and department |
| Conversation | At most six recent request/response text pairs, stored with the owner- and department-scoped session for 30 minutes |
| Proposal expiry | Existing `app.ai.action-ttl-seconds`, default five minutes |

Native enumeration skips hidden names and common dependency/build directories. The folder fallback filters by supported extension and name length. The UI reports when a scan limit is reached; choose a smaller folder to include omitted files. Tika OCR and embedded-document extraction are disabled. Scanned-image documents need OCR first; encrypted, unreadable and oversized documents are rejected with an explanation.

No source bytes or extracted source text are stored in database tables. Removing sources or closing the assistant attempts to delete their server context; source context expires after 30 minutes and is purged on the next source request. Recent conversation context is stored separately in the matching `ai_sessions` row, bounded to six user/assistant pairs and 40,000 characters, and scrubbed when its 30-minute expiry passes. Clearing a session deletes that stored context. It can contain patient information and is included in database backups. Multi-instance deployments still need `JSESSIONID` affinity for login and in-memory file context; see the [deployment guide](deployment.md#multiple-backend-instances). Reviewed workflow payloads persist in the existing pending-action table and may contain the imported record fields.

Local command mode remains deterministic and does not interpret files. It explicitly asks for an external model when file context is attached. No paid/live provider call was used during verification.

## API

- `POST /api/v1/assistant/sources`: multipart `file`; returns source ID, name, character count and expiry.
- `DELETE /api/v1/assistant/sources/{id}`: remove a source owned by the current user and department.
- `POST /api/v1/assistant/messages`: existing fields plus optional `sourceIds` and `connectedFiles: [{id,name}]`.
- `FILE_REQUEST`: returns manifest IDs to read. The browser resolves only IDs it currently owns, uploads those files and resends the request.
- `WORKFLOW_PROPOSAL`: returns the existing action envelope and a complete ordered workflow.
- Existing `/api/v1/ai-actions/{id}/confirm` and `/cancel` apply ownership, role, expiry and replay checks. Confirmation revalidates the workflow and executes through business services.

No model-generated file path, API URL, SQL statement or shell command is executed. Unknown tools and invalid workflow fields/references are rejected server-side. File contents and tool results are explicitly marked as untrusted model input. All writes require a reviewed proposal.

## Verification on 2026-09-20

Passed:

- Eight new PostgreSQL integration tests: linked hospital/patient/doctor/room/admission creation, atomic rollback, confirmation ownership/cancellation/expiry/replay, source ownership/removal, manifest validation, iterative queries, CSRF/format/size rejection, and actual Word/Excel/PDF extraction.
- Existing model and assistant authorization suites: 20 tests.
- Existing demo, registration and workspace-isolation suites: 16 tests.
- Frontend unit suite: 27 tests, including lazy file reads, revoked permissions, upload headers and department-bound assistant writes.
- TypeScript compilation and production Next.js build.
- Two Playwright journeys against the new backend: actual upload/removal with local-mode guidance; connected-file discovery, lazy upload, workflow review and explicit confirmation. The second uses a simulated browser directory handle and deterministic model/action responses; real workflow execution is covered by the PostgreSQL tests. Desktop and mobile screenshots were inspected.

Full backend verification also reproduced eight pre-existing failures (seven assertions and one error) in `HospitalIntegrationTest`. Running that suite from the unchanged starting commit `ea71fd3` against a separate fresh PostgreSQL database produced the same eight failures. Those older tests query department-filtered repositories outside request scope; they were not changed by this feature.

Test databases and browser servers were isolated from the running application. No real patient files were searched, uploaded or imported.
