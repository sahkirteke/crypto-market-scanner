CREATE TABLE market_scan_runs (
    id BIGSERIAL PRIMARY KEY,
    scan_time_utc TIMESTAMPTZ NOT NULL,
    scan_time_istanbul_text VARCHAR(64),
    scan_type VARCHAR(32) NOT NULL,
    market_regime VARCHAR(32),
    market_breadth_pct NUMERIC(20, 8),
    total_symbols INTEGER,
    prefilter_passed_count INTEGER,
    strong_long_count INTEGER,
    strong_short_count INTEGER,
    watchlist_count INTEGER,
    eliminated_count INTEGER,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    reasons_json TEXT,
    warnings_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_market_scan_runs_scan_time_utc ON market_scan_runs (scan_time_utc);
CREATE INDEX idx_market_scan_runs_scan_type ON market_scan_runs (scan_type);
CREATE INDEX idx_market_scan_runs_market_regime ON market_scan_runs (market_regime);
CREATE INDEX idx_market_scan_runs_status ON market_scan_runs (status);

CREATE TABLE coin_scan_results (
    id BIGSERIAL PRIMARY KEY,
    scan_run_id BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    direction_bias VARCHAR(32),
    classification VARCHAR(32) NOT NULL,
    score INTEGER,
    long_score INTEGER,
    short_score INTEGER,
    risk_level VARCHAR(32),
    last_price NUMERIC(30, 12),
    price_change_24h_pct NUMERIC(20, 8),
    quote_volume_24h NUMERIC(30, 8),
    spread_pct NUMERIC(20, 8),
    funding_rate NUMERIC(20, 10),
    open_interest NUMERIC(30, 8),
    market_breadth_pct NUMERIC(20, 8),
    reasons_json TEXT,
    warnings_json TEXT,
    eliminated_reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_coin_scan_results_scan_run
        FOREIGN KEY (scan_run_id) REFERENCES market_scan_runs (id) ON DELETE CASCADE
);

CREATE INDEX idx_coin_scan_results_scan_run_id ON coin_scan_results (scan_run_id);
CREATE INDEX idx_coin_scan_results_symbol ON coin_scan_results (symbol);
CREATE INDEX idx_coin_scan_results_classification ON coin_scan_results (classification);
CREATE INDEX idx_coin_scan_results_direction_bias ON coin_scan_results (direction_bias);
CREATE INDEX idx_coin_scan_results_score ON coin_scan_results (score);
CREATE INDEX idx_coin_scan_results_created_at ON coin_scan_results (created_at);
CREATE INDEX idx_coin_scan_results_scan_class_score
    ON coin_scan_results (scan_run_id, classification, score DESC);
