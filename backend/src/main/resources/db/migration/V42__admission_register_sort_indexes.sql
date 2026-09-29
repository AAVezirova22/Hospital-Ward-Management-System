-- Sortable admission-register columns (#164).
--
-- Sorting a clinical register on every visit is not free: the default view sorts on
-- admission_date_time, and the new patient and room keys join out to other tables.
-- These indexes exist so the sort is served from an index rather than by sorting
-- the whole department's admissions in memory on every page.
--
-- The room_assignments index is partial on released_at because the register's room
-- column means the assignment with no release time — a released assignment is
-- history, not where the patient is now.
create index if not exists admissions_register_date
  on admissions(department_id, admission_date_time desc, id desc);

create index if not exists admissions_register_status_date
  on admissions(department_id, status, admission_date_time desc, id desc);

-- Patients are joined by id and sorted by name, so the covering order here is
-- (last_name, first_name, id) within a department.
create index if not exists patients_register_name
  on patients(department_id, last_name, first_name, id);

-- A left join on the current assignment, so an admission with no current room must
-- still sort and still appear.
create index if not exists room_assignments_current_room
  on room_assignments(admission_id, room_id)
  where released_at is null;
