ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS entry_raw_signal VARCHAR(8);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS signal_inverted BOOLEAN NOT NULL DEFAULT TRUE;
