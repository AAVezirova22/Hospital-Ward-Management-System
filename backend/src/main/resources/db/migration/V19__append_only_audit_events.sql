-- Audit events are append-only for the application role. A controlled maintenance path (the
-- synthetic demo reset) opts in for its own transaction with:
--   select set_config('hospital.audit_maintenance', 'on', true)
-- Anyone who can alter or drop this trigger (the table owner, a superuser) can still change
-- history; see docs/deployment.md for the limits of this protection.
create function audit_events_append_only() returns trigger language plpgsql as $$
begin
  if coalesce(current_setting('hospital.audit_maintenance', true), '') = 'on' then
    if tg_op = 'DELETE' then
      return old;
    end if;
    return new;
  end if;
  raise exception 'audit_events is append-only: % is not allowed', tg_op;
end;
$$;

create trigger audit_events_append_only_rows
  before update or delete on audit_events
  for each row execute function audit_events_append_only();

create trigger audit_events_append_only_truncate
  before truncate on audit_events
  for each statement execute function audit_events_append_only();
