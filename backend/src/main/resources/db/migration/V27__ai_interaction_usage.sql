-- Token counts reported by an external model provider (#335). Prompts and replies are not stored.
alter table ai_interactions add column prompt_tokens bigint;
alter table ai_interactions add column completion_tokens bigint;
