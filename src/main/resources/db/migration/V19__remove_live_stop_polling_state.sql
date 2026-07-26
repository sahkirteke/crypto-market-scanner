ALTER TABLE laplace_paper_positions DROP COLUMN IF EXISTS min_bid_since_entry;
ALTER TABLE laplace_paper_positions DROP COLUMN IF EXISTS max_ask_since_entry;
ALTER TABLE laplace_paper_positions DROP COLUMN IF EXISTS last_stop_price_checked_at;
ALTER TABLE laplace_paper_positions DROP COLUMN IF EXISTS last_observed_bid;
ALTER TABLE laplace_paper_positions DROP COLUMN IF EXISTS last_observed_ask;
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS last_stop_checked_candle_open_time TIMESTAMPTZ;
