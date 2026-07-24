CREATE TABLE IF NOT EXISTS laplace_coin_pool (
 symbol VARCHAR(32) PRIMARY KEY, status VARCHAR(32) NOT NULL, quote_volume NUMERIC(30,8),
 added_at TIMESTAMP WITH TIME ZONE, removed_at TIMESTAMP WITH TIME ZONE,
 last_observed_trend VARCHAR(16), trend_lock_completed BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT
);
