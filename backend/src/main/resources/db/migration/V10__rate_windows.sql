create table if not exists rate_windows (
  rate_key varchar(190) primary key,
  window_start timestamptz not null,
  hit_count integer not null
);
create index if not exists rate_windows_started on rate_windows (window_start);
