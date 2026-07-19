ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS entry_reference_price NUMERIC(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS entry_slippage_pct NUMERIC(20,10);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS exit_reference_price NUMERIC(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS exit_slippage_pct NUMERIC(20,10);
