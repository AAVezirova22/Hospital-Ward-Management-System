create table email_outbox (
  id bigserial primary key,
  user_id bigint not null references app_users(id) on delete cascade,
  recipient varchar(254),
  first_name varchar(100),
  confirmation_token varchar(64),
  doctor boolean not null,
  expiry_minutes integer not null check (expiry_minutes > 0),
  expires_at timestamptz not null,
  status varchar(16) not null default 'PENDING'
    check (status in ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED', 'EXPIRED')),
  attempts integer not null default 0 check (attempts >= 0),
  next_attempt_at timestamptz not null default now(),
  lease_until timestamptz,
  sent_at timestamptz,
  last_error varchar(64),
  created_at timestamptz not null default now(),
  check ((status in ('PENDING', 'SENDING')) =
      (recipient is not null and first_name is not null and confirmation_token is not null))
);

create index email_outbox_due on email_outbox(status, next_attempt_at, lease_until, id);
create index email_outbox_user on email_outbox(user_id, id);
