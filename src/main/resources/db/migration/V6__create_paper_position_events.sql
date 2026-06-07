CREATE TABLE IF NOT EXISTS paper_position_events (
    id BIGSERIAL PRIMARY KEY,
    position_id BIGINT NOT NULL,
    event_time_utc TIMESTAMPTZ NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    price NUMERIC(30,12),
    adjusted_price NUMERIC(30,12),
    position_pct_closed NUMERIC(20,8),
    raw_pnl_pct NUMERIC(20,8),
    net_pnl_pct NUMERIC(20,8),
    leveraged_net_pnl_pct NUMERIC(20,8),
    fee_pct NUMERIC(20,8),
    slippage_pct NUMERIC(20,8),
    leverage INTEGER,
    reason VARCHAR(128),
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_paper_position_events_position FOREIGN KEY (position_id) REFERENCES paper_positions(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_paper_position_events_position_id ON paper_position_events(position_id);
CREATE INDEX IF NOT EXISTS idx_paper_position_events_event_time ON paper_position_events(event_time_utc);
CREATE INDEX IF NOT EXISTS idx_paper_position_events_event_type ON paper_position_events(event_type);
