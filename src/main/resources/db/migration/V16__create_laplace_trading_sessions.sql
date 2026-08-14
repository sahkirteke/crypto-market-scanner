CREATE TABLE laplace_trading_sessions (
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

ALTER TABLE laplace_paper_positions ADD COLUMN session_id VARCHAR(36);
CREATE INDEX idx_laplace_positions_session ON laplace_paper_positions(session_id);
