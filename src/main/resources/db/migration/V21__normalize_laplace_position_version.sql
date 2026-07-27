UPDATE laplace_paper_positions
SET version = 0
WHERE version IS NULL;

ALTER TABLE laplace_paper_positions
    ALTER COLUMN version SET DEFAULT 0;

ALTER TABLE laplace_paper_positions
    ALTER COLUMN version SET NOT NULL;
