CREATE TABLE IF NOT EXISTS coin_scan_forward_metrics (
    id BIGSERIAL PRIMARY KEY,
    coin_scan_result_id BIGINT NOT NULL,
    scan_run_id BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    forward_return_1h_pct NUMERIC(20,8),
    forward_return_4h_pct NUMERIC(20,8),
    forward_return_8h_pct NUMERIC(20,8),
    forward_return_24h_pct NUMERIC(20,8),
    max_forward_gain_pct NUMERIC(20,8),
    max_forward_drawdown_pct NUMERIC(20,8),
    calculated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_forward_metrics_coin_scan_result FOREIGN KEY (coin_scan_result_id) REFERENCES coin_scan_results(id) ON DELETE CASCADE,
    CONSTRAINT fk_forward_metrics_scan_run FOREIGN KEY (scan_run_id) REFERENCES market_scan_runs(id) ON DELETE CASCADE,
    CONSTRAINT unique_coin_scan_forward_metrics_result_id UNIQUE (coin_scan_result_id)
);
CREATE INDEX IF NOT EXISTS idx_forward_metrics_scan_run_id ON coin_scan_forward_metrics(scan_run_id);
CREATE INDEX IF NOT EXISTS idx_forward_metrics_symbol ON coin_scan_forward_metrics(symbol);
CREATE INDEX IF NOT EXISTS idx_forward_metrics_calculated_at ON coin_scan_forward_metrics(calculated_at);
