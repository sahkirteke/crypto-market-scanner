ALTER TABLE laplace_coin_pool
    ADD COLUMN IF NOT EXISTS risky_entry_pending BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE laplace_coin_pool
    ADD COLUMN IF NOT EXISTS pending_risky_raw_signal VARCHAR(16);

ALTER TABLE laplace_coin_pool
    ADD COLUMN IF NOT EXISTS pending_risky_signal_candle_close_time TIMESTAMPTZ;
