ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS current_price NUMERIC(30, 12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS highest_price NUMERIC(30, 12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS lowest_price NUMERIC(30, 12);

ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS max_favorable_move_pct NUMERIC(20, 8);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS max_adverse_move_pct NUMERIC(20, 8);

ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS bars_held INTEGER;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS minutes_held INTEGER;

ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS entry_signal_score INTEGER;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS last_checked_at TIMESTAMPTZ;

ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS take_profit_pct NUMERIC(20, 8);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS stop_loss_pct NUMERIC(20, 8);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS time_stop_minutes INTEGER;

ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS exit_detail TEXT;

CREATE INDEX IF NOT EXISTS idx_paper_positions_last_checked_at ON paper_positions(last_checked_at);
CREATE INDEX IF NOT EXISTS idx_paper_positions_status_symbol ON paper_positions(status, symbol);
