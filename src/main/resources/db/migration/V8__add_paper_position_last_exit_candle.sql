ALTER TABLE paper_positions
ADD COLUMN IF NOT EXISTS last_exit_candle_close_time TIMESTAMPTZ;
