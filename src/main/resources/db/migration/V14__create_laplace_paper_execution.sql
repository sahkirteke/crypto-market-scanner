CREATE TABLE IF NOT EXISTS laplace_paper_positions (
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
CREATE UNIQUE INDEX IF NOT EXISTS uq_laplace_open_position ON laplace_paper_positions(strategy,symbol) WHERE status='OPEN';
CREATE TABLE IF NOT EXISTS laplace_trade_events (
 event_id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(24) NOT NULL, reversal_id VARCHAR(36),
 position_id VARCHAR(36), symbol VARCHAR(32) NOT NULL, payload_json TEXT NOT NULL,
 jsonl_written BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL, written_at TIMESTAMPTZ
);
