alter table department_memberships
  add column patient_import_enabled boolean not null default false;
