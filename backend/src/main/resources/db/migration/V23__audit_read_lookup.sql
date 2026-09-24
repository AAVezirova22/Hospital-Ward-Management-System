-- Supports the repeat-view check for patient and admission read events.
create index audit_read_lookup on audit_events(user_id, event_type, entity_id, timestamp);
