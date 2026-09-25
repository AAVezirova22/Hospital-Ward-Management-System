alter table task_reminders
  add column subscription_id bigint references push_subscriptions(id) on delete cascade;

alter table task_reminders
  drop constraint task_reminders_task_id_recipient_user_id_due_at_key;

-- Preserve existing delivery history by assigning legacy rows to one active device.
update task_reminders r
   set subscription_id = (
     select s.id from push_subscriptions s
      where s.user_id = r.recipient_user_id and s.revoked_at is null
      order by s.id limit 1
   )
 where r.subscription_id is null;

alter table task_reminders
  add constraint task_reminders_subscription_unique
  unique (task_id, recipient_user_id, due_at, subscription_id);

create index task_reminders_subscription_idx on task_reminders(subscription_id, status);
