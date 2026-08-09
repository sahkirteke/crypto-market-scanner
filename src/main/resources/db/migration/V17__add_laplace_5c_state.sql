ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS fast_exit_mode BOOLEAN;
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS positive_breadth30_pct NUMERIC(10,4);
UPDATE laplace_paper_positions SET fast_exit_mode=FALSE WHERE fast_exit_mode IS NULL;
