CREATE TABLE paper_positions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    entry_action VARCHAR(32) NOT NULL,
    entry_price NUMERIC(30, 12) NOT NULL,
    quantity NUMERIC(30, 12) NOT NULL,
    notional_usdt NUMERIC(30, 8) NOT NULL,
    leverage INTEGER NOT NULL,
    entry_score INTEGER,
    long_score INTEGER,
    short_score INTEGER,
    source_classification VARCHAR(32),
    direction_bias VARCHAR(32),
    risk_level VARCHAR(32),
    funding_rate NUMERIC(20, 10),
    open_interest NUMERIC(30, 8),
    market_breadth_pct NUMERIC(20, 8),
    price_change_24h_pct NUMERIC(20, 8),
    spread_pct NUMERIC(20, 8),
    entry_reason VARCHAR(128),
    signal_reason VARCHAR(128),
    reasons_json TEXT,
    warnings_json TEXT,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    exit_price NUMERIC(30, 12),
    realized_pnl_usdt NUMERIC(30, 8),
    realized_pnl_pct NUMERIC(20, 8),
    exit_reason VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_paper_positions_symbol ON paper_positions (symbol);
CREATE INDEX idx_paper_positions_status ON paper_positions (status);
CREATE INDEX idx_paper_positions_side ON paper_positions (side);
CREATE INDEX idx_paper_positions_opened_at ON paper_positions (opened_at);
CREATE INDEX idx_paper_positions_symbol_status ON paper_positions (symbol, status);
CREATE INDEX idx_paper_positions_status_opened_at ON paper_positions (status, opened_at);
