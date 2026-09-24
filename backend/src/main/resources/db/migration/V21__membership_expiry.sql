-- Optional end of a department membership (#347). Access stops at expires_at; the row stays for
-- history until an administrator extends or removes it. expiry_notified_at records the advance notice.
alter table department_memberships add column expires_at timestamptz;
alter table department_memberships add column expiry_notified_at timestamptz;
create index department_memberships_expiring on department_memberships(expires_at)
  where expires_at is not null and expiry_notified_at is null;
