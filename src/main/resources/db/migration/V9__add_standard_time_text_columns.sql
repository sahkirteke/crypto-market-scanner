ALTER TABLE market_scan_runs ADD COLUMN IF NOT EXISTS scan_time_text VARCHAR(64);

ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS opened_at_text VARCHAR(64);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS closed_at_text VARCHAR(64);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS last_checked_at_text VARCHAR(64);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS created_at_text VARCHAR(64);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS updated_at_text VARCHAR(64);

ALTER TABLE paper_position_events ADD COLUMN IF NOT EXISTS event_time_text VARCHAR(64);
ALTER TABLE paper_position_events ADD COLUMN IF NOT EXISTS created_at_text VARCHAR(64);

ALTER TABLE entry_candidates ADD COLUMN IF NOT EXISTS valid_from_text VARCHAR(64);
ALTER TABLE entry_candidates ADD COLUMN IF NOT EXISTS valid_until_text VARCHAR(64);
ALTER TABLE entry_candidates ADD COLUMN IF NOT EXISTS created_at_text VARCHAR(64);
ALTER TABLE entry_candidates ADD COLUMN IF NOT EXISTS updated_at_text VARCHAR(64);
