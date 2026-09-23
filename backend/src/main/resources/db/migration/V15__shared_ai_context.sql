alter table ai_sessions
  add column conversation_context text,
  add column conversation_expires_at timestamptz;

create index ai_sessions_conversation_expiry
  on ai_sessions (conversation_expires_at)
  where conversation_context is not null;
