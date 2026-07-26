CREATE TABLE laplace_volume_scan_audit_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(220) NOT NULL UNIQUE,
    scan_run_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    symbol VARCHAR(32),
    payload_json TEXT NOT NULL,
    jsonl_written BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    written_at TIMESTAMPTZ
);

CREATE INDEX idx_laplace_volume_audit_pending
    ON laplace_volume_scan_audit_outbox(jsonl_written, created_at);
