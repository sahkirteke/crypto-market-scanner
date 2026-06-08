ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS symbol_trade_entry_logged BOOLEAN DEFAULT false;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS symbol_trade_exit_logged BOOLEAN DEFAULT false;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS market_regime VARCHAR(32);
