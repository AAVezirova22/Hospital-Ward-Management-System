-- One-time tokens that let a user retry an assistant request after a transient provider failure
-- (#338). Only a hash of the original message is kept, never the message itself.
alter table ai_interactions add column retry_token varchar(64);
alter table ai_interactions add column retry_message_hash varchar(64);
alter table ai_interactions add column retry_attempt integer not null default 0;
create unique index ai_interactions_retry_token on ai_interactions(retry_token) where retry_token is not null;
