ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS symbol_trade_entry_logged BOOLEAN DEFAULT false;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS symbol_trade_tp1_exit_logged BOOLEAN DEFAULT false;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS symbol_trade_tp2_exit_logged BOOLEAN DEFAULT false;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS symbol_trade_final_exit_logged BOOLEAN DEFAULT false;
