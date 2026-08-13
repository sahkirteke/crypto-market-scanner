CREATE TABLE IF NOT EXISTS laplace_trading_sessions (
 session_id VARCHAR(32) PRIMARY KEY, start_time TIMESTAMPTZ NOT NULL,
 entry_cutoff_time TIMESTAMPTZ NOT NULL, end_time TIMESTAMPTZ NOT NULL,
 starting_capital NUMERIC(30,12) NOT NULL, starting_margin NUMERIC(30,12) NOT NULL,
 realized_pnl NUMERIC(30,12) NOT NULL DEFAULT 0, final_capital NUMERIC(30,12),
 final_return_pct NUMERIC(20,12), next_session_margin NUMERIC(30,12),
 status VARCHAR(32) NOT NULL, entry_locked BOOLEAN NOT NULL DEFAULT TRUE,
 profit_target_locked BOOLEAN NOT NULL DEFAULT FALSE, profit_target_locked_at TIMESTAMPTZ,
 completed_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_laplace_sessions_status_start ON laplace_trading_sessions(status,start_time DESC);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS session_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_laplace_positions_session ON laplace_paper_positions(session_id);
