CREATE TABLE IF NOT EXISTS laplace_inverted_false_positions (
 id VARCHAR(36) PRIMARY KEY, strategy VARCHAR(64) NOT NULL, strategy_version VARCHAR(16) NOT NULL,
 symbol VARCHAR(32) NOT NULL, side VARCHAR(8) NOT NULL, status VARCHAR(16) NOT NULL,
 entry_signal_id VARCHAR(160) NOT NULL UNIQUE, entry_candle_close_time TIMESTAMPTZ NOT NULL,
 entry_time TIMESTAMPTZ NOT NULL, entry_signal_close_price NUMERIC(30,12) NOT NULL,
 entry_execution_price NUMERIC(30,12) NOT NULL, margin NUMERIC(30,12) NOT NULL,
 quantity NUMERIC(30,12) NOT NULL, notional NUMERIC(30,8) NOT NULL, leverage INTEGER NOT NULL,
 entry_fee_rate NUMERIC(20,10) NOT NULL, entry_fee NUMERIC(30,12) NOT NULL,
 exit_time TIMESTAMPTZ, exit_execution_price NUMERIC(30,12), exit_fee NUMERIC(30,12),
 gross_pnl NUMERIC(30,12), gross_pnl_pct NUMERIC(20,8), net_pnl NUMERIC(30,12),
 net_pnl_pct NUMERIC(20,8), holding_minutes BIGINT, exit_reason VARCHAR(80),
 exit_signal_id VARCHAR(160) UNIQUE, version BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_laplace_false_open_position ON laplace_inverted_false_positions(strategy,symbol) WHERE status='OPEN';
CREATE TABLE IF NOT EXISTS laplace_inverted_false_trade_events (
 event_id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(24) NOT NULL, reversal_id VARCHAR(36),
 position_id VARCHAR(36), symbol VARCHAR(32) NOT NULL, payload_json TEXT NOT NULL,
 jsonl_written BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL, written_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS laplace_inverted_false_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    session_start_time TIMESTAMPTZ NOT NULL,
    session_start_capital NUMERIC(30,12) NOT NULL,
    margin_per_position NUMERIC(30,12) NOT NULL,
    starting_notional NUMERIC(30,12) NOT NULL,
    leverage INTEGER NOT NULL,
    profit_target_pct NUMERIC(10,6) NOT NULL,
    minimum_locked_profit_pct NUMERIC(10,6) NOT NULL,
    profit_target_usdt NUMERIC(30,12) NOT NULL,
    minimum_locked_profit_usdt NUMERIC(30,12) NOT NULL,
    realized_session_net_pnl NUMERIC(30,12) NOT NULL,
    runtime_state VARCHAR(16) NOT NULL,
    cooldown_started_at TIMESTAMPTZ,
    cooldown_until TIMESTAMPTZ,
    open_position_pnl NUMERIC(30,12),
    estimated_exit_fees NUMERIC(30,12),
    estimated_net_profit_after_close NUMERIC(30,12),
    actual_locked_session_profit NUMERIC(30,12),
    capital_growth_factor NUMERIC(30,12),
    next_session_capital NUMERIC(30,12),
    next_margin_per_position NUMERIC(30,12),
    next_position_notional NUMERIC(30,12),
    profit_lock_triggered_at TIMESTAMPTZ,
    version BIGINT
);

ALTER TABLE laplace_inverted_false_positions ADD COLUMN IF NOT EXISTS session_id VARCHAR(36);
CREATE INDEX idx_laplace_false_positions_session ON laplace_inverted_false_positions(session_id);
