create table patient_correction_requests (
  id bigserial primary key,
  department_id bigint not null references departments(id),
  patient_id bigint not null references patients(id),
  requested_by bigint not null references app_users(id),
  first_name varchar(100),
  last_name varchar(100),
  date_of_birth date,
  address varchar(500),
  phone_number varchar(40),
  status varchar(20) not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  review_note varchar(300),
  reviewed_by bigint references app_users(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  check (first_name is not null or last_name is not null or date_of_birth is not null or address is not null or phone_number is not null)
);
create index patient_correction_requests_department_status_idx
  on patient_correction_requests(department_id, status, created_at desc);
