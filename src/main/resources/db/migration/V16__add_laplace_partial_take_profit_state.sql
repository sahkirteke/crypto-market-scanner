ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS initial_quantity DECIMAL(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS remaining_quantity DECIMAL(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS tp1_executed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS tp2_executed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS tp1_closed_quantity DECIMAL(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS tp2_closed_quantity DECIMAL(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS tp1_realized_pnl DECIMAL(30,12);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS tp2_realized_pnl DECIMAL(30,12);
