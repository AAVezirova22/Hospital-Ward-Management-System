alter table hospital_memberships
  add column joined_at timestamptz;

alter table department_memberships
  add column joined_at timestamptz;

-- Existing membership dates are unknown; leave them NULL rather than inventing a join date.
alter table hospital_memberships alter column joined_at set default now();
alter table department_memberships alter column joined_at set default now();

create index hospital_memberships_roster
  on hospital_memberships(hospital_id, joined_at desc nulls last, user_id desc);
create index department_memberships_roster
  on department_memberships(department_id, joined_at desc nulls last, user_id desc);
