alter table departments add column archived_at timestamptz;

create or replace function reject_archived_department_write() returns trigger
language plpgsql as $$
declare
  target_department_id bigint;
  previous_department_id bigint;
begin
  if tg_op = 'DELETE' then
    target_department_id := old.department_id;
  else
    target_department_id := new.department_id;
    if tg_op = 'UPDATE' then
      previous_department_id := old.department_id;
    end if;
  end if;

  -- Serialize department writes with archive/restore so a write cannot commit after archival.
  perform 1 from departments where id = target_department_id for share;
  if previous_department_id is not null and previous_department_id <> target_department_id then
    perform 1 from departments where id = previous_department_id for share;
  end if;
  if exists (select 1 from departments where id in (target_department_id, previous_department_id) and archived_at is not null) then
    raise exception 'Department % is archived and read only', target_department_id
      using errcode = '23514', constraint = 'department_archived_read_only';
  end if;
  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$$;

do $$
declare
  target_table record;
begin
  for target_table in
    select c.table_name
      from information_schema.columns c
      join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name
     where c.table_schema = current_schema()
       and c.column_name = 'department_id'
       and c.table_name <> 'audit_events'
       and t.table_type = 'BASE TABLE'
  loop
    execute format('create trigger reject_archived_department_write before insert or update or delete on %I for each row execute function reject_archived_department_write()', target_table.table_name);
  end loop;
end;
$$;
