ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS market_retry_pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS pending_market_raw_signal VARCHAR(16);
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS pending_market_rejection_reason VARCHAR(64);
ALTER TABLE laplace_coin_pool ADD COLUMN IF NOT EXISTS pending_market_signal_candle_close_time TIMESTAMPTZ;

-- Older builds stored every filter rejection in the risky fields, without recording
-- its rejection reason on the pool row.  A migration cannot safely distinguish a
-- genuine risk block from a market rejection, so it deliberately does not delete
-- any existing block.  For a clean paper-trading reset, first archive the audit
-- tables, then clear these three risky fields manually at a declared cut-over time.
