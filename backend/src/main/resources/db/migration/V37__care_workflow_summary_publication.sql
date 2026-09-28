-- A patient-facing summary is published by its own clinician action, separately from the
-- approval that launches care tasks. Before this, approving a pending run wrote the summary
-- and activated the run in one statement, so a single approval both launched tasks and
-- released text to the patient portal. patientSummaries() now returns only runs whose
-- summary has been explicitly published, which also makes a wrong summary retractable
-- without cancelling the whole pathway.
--
-- Backfill: every summary already visible in the portal was, by definition, already published
-- under the old single-approval model. Leaving summary_published_at null for those rows would
-- silently remove every summary a patient can currently see, so mark them published using
-- reviewed_at/reviewed_by - the existing record of the clinician who approved it - rather than
-- an invented attribution.
alter table care_workflow_runs add column summary_published_by bigint references app_users(id);
alter table care_workflow_runs add column summary_published_at timestamptz;

update care_workflow_runs
   set summary_published_at = reviewed_at,
       summary_published_by = reviewed_by
 where patient_summary is not null
   and patient_summary <> ''
   and summary_published_at is null
   and reviewed_at is not null;

-- A row with an approved summary but no review timestamp predates the reviewed_at column and
-- cannot be attributed. It stays unpublished rather than getting an invented publisher: a
-- clinician must re-approve it, which is the safer failure for patient-facing text.

