ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS prev3_5m_return NUMERIC(20,12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS early_exit_triggered BOOLEAN;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS early_exit_rule_a BOOLEAN;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS early_exit_rule_b BOOLEAN;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS first15_high_pct NUMERIC(20,12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS close15_pct NUMERIC(20,12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS early_exit_price NUMERIC(30,12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS early_exit_time TIMESTAMPTZ;
