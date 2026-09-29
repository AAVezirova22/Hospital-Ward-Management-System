-- Read-only archival for departments that have stopped operating (#162).
--
-- A department is never deleted: its admissions, performed procedures and reports
-- are the record of what happened while it ran, and removing the unit would either
-- orphan that history or destroy it. Archiving instead marks the unit closed, which
-- blocks new clinical writes while leaving every existing row readable and every
-- historical report reproducible.
--
-- status is deliberately a lifecycle column rather than a boolean so a later state
-- (for example a wind-down that still permits discharges) can be added without a
-- schema migration per state.
alter table departments
  add column status varchar(20) not null default 'ACTIVE'
    check(status in ('ACTIVE','ARCHIVED')),
  add column archived_at timestamptz,
  add column archived_by bigint references app_users(id),
  add column archive_reason varchar(500);

-- An archived unit has been closed at a known time by a known user, or is still
-- active and has neither. Anything in between is a half-applied archive.
alter table departments add constraint departments_archive_consistent
  check ((status = 'ACTIVE' and archived_at is null and archived_by is null)
      or (status = 'ARCHIVED' and archived_at is not null and archived_by is not null));

create index departments_status on departments(status);

-- The join code must stop working the moment a unit is archived, so it cannot be
-- used to add new members to a closed unit. The code itself is kept so the audit
-- trail stays readable.
create or replace function departments_block_archived_join()
returns trigger language plpgsql as $$
begin
  if exists (select 1 from departments where id = new.department_id and status = 'ARCHIVED') then
    raise exception 'Department % is archived and cannot accept new members', new.department_id
      using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

create trigger departments_block_archived_join
  before insert or update on department_memberships
  for each row execute function departments_block_archived_join();
