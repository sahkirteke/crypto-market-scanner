CREATE TABLE laplace_coin_pool (
 symbol VARCHAR(32) PRIMARY KEY, state VARCHAR(32) NOT NULL, last_quote_volume NUMERIC(30,12),
 added_at TIMESTAMPTZ, removed_at TIMESTAMPTZ, pending_removal_at TIMESTAMPTZ,
 last_observed_trend VARCHAR(16), waiting_for_new_trend BOOLEAN NOT NULL DEFAULT TRUE,
 last_volume_scan_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE laplace_strategy_capital_snapshots (
 snapshot_date DATE PRIMARY KEY, realized_capital NUMERIC(30,12) NOT NULL,
 daily_trade_margin NUMERIC(30,12) NOT NULL, leverage INTEGER NOT NULL,
 position_notional NUMERIC(30,12) NOT NULL, calculated_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE laplace_paper_positions ADD COLUMN stop_loss_pct NUMERIC(20,10);
ALTER TABLE laplace_paper_positions ADD COLUMN stop_price NUMERIC(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN last_checked_5m_candle_close_time TIMESTAMPTZ;
ALTER TABLE laplace_paper_positions ADD COLUMN stop_triggered_candle_open_time TIMESTAMPTZ;
ALTER TABLE laplace_paper_positions ADD COLUMN stop_triggered_candle_close_time TIMESTAMPTZ;
ALTER TABLE laplace_paper_positions ADD COLUMN stop_triggered_candle_high NUMERIC(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN stop_triggered_candle_low NUMERIC(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN stop_triggered_candle_close NUMERIC(30,12);
