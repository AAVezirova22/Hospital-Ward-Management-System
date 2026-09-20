alter table app_users drop constraint app_users_role_check;
alter table app_users add constraint app_users_role_check check (role in ('ADMIN','MEDICAL_STAFF','DOCTOR','PATIENT'));
alter table app_users add column email varchar(254) unique;
alter table app_users add column email_verified boolean not null default false;
alter table app_users add column requested_role varchar(30);
alter table app_users add column patient_id bigint unique references patients(id);
create table email_verifications (token_hash varchar(64) primary key, user_id bigint not null references app_users(id), expires_at timestamptz not null, used_at timestamptz);
create index verification_user on email_verifications(user_id);
