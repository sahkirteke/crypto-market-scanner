CREATE TABLE laplace_symbol_blocks (
    id VARCHAR(36) PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    blocked_at TIMESTAMPTZ NOT NULL,
    blocked_until TIMESTAMPTZ NOT NULL,
    source_position_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_laplace_symbol_blocks_active ON laplace_symbol_blocks(symbol, blocked_until);
