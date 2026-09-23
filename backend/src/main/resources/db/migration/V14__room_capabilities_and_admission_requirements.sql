create table room_capabilities (
  room_id bigint not null references rooms(id) on delete cascade,
  capability varchar(64) not null,
  primary key (room_id, capability)
);

create table admission_room_requirements (
  admission_id bigint not null references admissions(id) on delete cascade,
  capability varchar(64) not null,
  primary key (admission_id, capability)
);
