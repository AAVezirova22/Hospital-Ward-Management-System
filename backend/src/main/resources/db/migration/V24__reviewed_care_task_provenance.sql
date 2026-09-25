alter table care_tasks add column due_on date;
alter table care_tasks add column due_time time;
alter table care_tasks add column task_origin varchar(20) not null default 'TEMPLATE'
  check (task_origin in ('TEMPLATE','OVERRIDDEN','DOCUMENT','MANUAL'));
alter table care_tasks add column source_reference varchar(200);
alter table care_tasks add column source_name varchar(200);
alter table care_tasks add column source_excerpt text;
alter table care_tasks add column source_location varchar(200);
alter table care_tasks add column source_confidence double precision;
alter table care_tasks add column review_decision varchar(20)
  check (review_decision is null or review_decision in ('ACCEPTED','EDITED'));
alter table care_tasks add column source_edited boolean not null default false;
alter table care_tasks add column reviewed_by bigint references app_users(id);
alter table care_tasks add column reviewed_at timestamptz;
