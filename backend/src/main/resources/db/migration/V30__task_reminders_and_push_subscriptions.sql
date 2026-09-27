create table task_reminder_preferences (
    user_id bigint primary key references app_users(id) on delete cascade,
    opted_in boolean not null default false,
    time_zone varchar(80) not null default 'UTC',
    minutes_before integer not null default 10 check (minutes_before in (0, 5, 10, 15, 30, 60)),
    quiet_hours_start time,
    quiet_hours_end time,
    operational_alerts boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((quiet_hours_start is null) = (quiet_hours_end is null))
);

create table push_subscriptions (
    id bigserial primary key,
    user_id bigint not null references app_users(id) on delete cascade,
    endpoint text not null,
    p256dh_key varchar(256) not null,
    auth_secret varchar(256) not null,
    created_at timestamptz not null default now(),
    last_used_at timestamptz,
    revoked_at timestamptz,
    unique (user_id, endpoint)
);
create index push_subscriptions_active_user_idx on push_subscriptions(user_id) where revoked_at is null;

create table task_reminders (
    id bigserial primary key,
    department_id bigint not null references departments(id) on delete cascade,
    task_id bigint not null,
    recipient_user_id bigint not null references app_users(id) on delete cascade,
    due_at timestamptz not null,
    scheduled_at timestamptz not null,
    snoozed_until timestamptz,
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
    unique (task_id, recipient_user_id, due_at),
    foreign key (department_id, task_id) references care_tasks(department_id, id) on delete cascade
);
create index task_reminders_due_idx on task_reminders(status, next_attempt_at, scheduled_at);
create index task_reminders_recipient_idx on task_reminders(recipient_user_id, created_at desc);
