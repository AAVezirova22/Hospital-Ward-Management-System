-- Global sentinel (id 0) for cross-department operations such as demo reset.
-- One lock row per department so Hospital A no longer blocks Hospital B.
insert into workflow_lock(id)
select 0
where not exists (select 1 from workflow_lock where id = 0);

insert into workflow_lock(id)
select d.id
from departments d
where not exists (select 1 from workflow_lock w where w.id = d.id);
