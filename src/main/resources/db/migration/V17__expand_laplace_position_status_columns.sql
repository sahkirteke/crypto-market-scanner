ALTER TABLE laplace_paper_positions
    ALTER COLUMN status TYPE VARCHAR(32);

-- Existing Laplace signal-exit reasons are longer than 32 characters, so the
-- established 80-character capacity is retained explicitly alongside status.
ALTER TABLE laplace_paper_positions
    ALTER COLUMN exit_reason TYPE VARCHAR(80);
