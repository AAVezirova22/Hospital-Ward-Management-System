with ranked as (
  select id, row_number() over (partition by lower(name) order by id) as n
  from hospitals
)
update hospitals h
set name = h.name || ' (' || ranked.n || ')'
from ranked
where h.id = ranked.id and ranked.n > 1;

create unique index hospitals_name_unique on hospitals (lower(name));
