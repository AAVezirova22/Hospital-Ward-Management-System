alter table hospitals alter column join_code type varchar(40);
alter table departments alter column join_code type varchar(40);

update hospitals
set join_code = 'H-' || join_code
where join_code not like 'H-%' and join_code not like 'D-%';

update departments
set join_code = 'D-' || join_code
where join_code not like 'H-%' and join_code not like 'D-%';
