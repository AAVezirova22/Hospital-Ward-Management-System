create table operational_push_deliveries (
    id bigserial primary key,
    department_id bigint not null references departments(id) on delete cascade,
    notification_id bigint not null references notifications(id) on delete cascade,
    recipient_user_id bigint not null references app_users(id) on delete cascade,
    subscription_id bigint not null references push_subscriptions(id) on delete cascade,
    notice_version bigint not null,
    action_token uuid not null unique,
    status varchar(16) not null default 'PENDING'
        check (status in ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    attempt_count integer not null default 0,
    last_attempt_at timestamptz,
    next_attempt_at timestamptz not null default now(),
    sent_at timestamptz,
    error_code varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (notification_id, recipient_user_id, subscription_id, notice_version)
);
create index operational_push_due_idx on operational_push_deliveries(status, next_attempt_at);
create index operational_push_recipient_idx on operational_push_deliveries(recipient_user_id, created_at desc);
