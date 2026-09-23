alter table departments
  add column time_zone varchar(64) not null default 'UTC';
