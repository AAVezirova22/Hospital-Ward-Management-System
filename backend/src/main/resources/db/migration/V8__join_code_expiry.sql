alter table hospitals add column if not exists join_code_expires_at timestamptz;
alter table hospitals add column if not exists join_code_single_use boolean not null default false;
alter table departments add column if not exists join_code_expires_at timestamptz;
alter table departments add column if not exists join_code_single_use boolean not null default false;
