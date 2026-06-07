CREATE TABLE IF NOT EXISTS entry_candidates (
    id BIGSERIAL PRIMARY KEY,
    scan_run_id BIGINT NOT NULL,
    coin_scan_result_id BIGINT,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    score INTEGER,
    entry_priority_score INTEGER,
    risk_level VARCHAR(32),
    market_regime VARCHAR(32),
    market_breadth_pct NUMERIC(20, 8),
    valid_from_utc TIMESTAMPTZ NOT NULL,
    valid_until_utc TIMESTAMPTZ NOT NULL,
    reasons_json TEXT,
    warnings_json TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_entry_candidates_scan_run FOREIGN KEY (scan_run_id) REFERENCES market_scan_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_entry_candidates_coin_scan_result FOREIGN KEY (coin_scan_result_id) REFERENCES coin_scan_results(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_entry_candidates_scan_run_id ON entry_candidates(scan_run_id);
CREATE INDEX IF NOT EXISTS idx_entry_candidates_symbol ON entry_candidates(symbol);
CREATE INDEX IF NOT EXISTS idx_entry_candidates_status ON entry_candidates(status);
CREATE INDEX IF NOT EXISTS idx_entry_candidates_valid_until ON entry_candidates(valid_until_utc);
CREATE INDEX IF NOT EXISTS idx_entry_candidates_symbol_status ON entry_candidates(symbol, status);
