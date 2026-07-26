ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS stopped_raw_signal_side VARCHAR(16);
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS stopped_position_side VARCHAR(8);
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS stopped_position_id VARCHAR(36);
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS stopped_at TIMESTAMPTZ;
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS reentry_blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS unblocked_by_signal_side VARCHAR(16);
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS unblocked_at TIMESTAMPTZ;
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS min_bid_since_entry NUMERIC(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS max_ask_since_entry NUMERIC(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS last_stop_price_checked_at TIMESTAMPTZ;
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS last_observed_bid NUMERIC(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS last_observed_ask NUMERIC(30,12);

DELETE FROM laplace_trade_events duplicate
USING laplace_trade_events retained
WHERE duplicate.event_type = 'EXIT'
  AND retained.event_type = 'EXIT'
  AND duplicate.position_id = retained.position_id
  AND duplicate.position_id IS NOT NULL
  AND duplicate.ctid > retained.ctid;

CREATE UNIQUE INDEX IF NOT EXISTS uq_laplace_exit_event_per_position
    ON laplace_trade_events(position_id, event_type)
    WHERE event_type = 'EXIT' AND position_id IS NOT NULL;
