-- A patient-facing summary is published by its own clinician action, separately from the
-- approval that launches care tasks. Before this, approving a pending run wrote the summary
-- and activated the run in one statement, so a single approval both launched tasks and
-- released text to the patient portal. patientSummaries() now returns only runs whose
-- summary has been explicitly published, which also makes a wrong summary retractable
-- without cancelling the whole pathway.
alter table care_workflow_runs add column summary_published_by bigint references app_users(id);
alter table care_workflow_runs add column summary_published_at timestamptz;
