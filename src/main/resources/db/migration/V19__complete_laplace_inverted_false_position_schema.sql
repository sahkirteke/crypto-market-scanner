ALTER TABLE laplace_inverted_false_positions
    ADD COLUMN IF NOT EXISTS entry_raw_signal VARCHAR(8);

ALTER TABLE laplace_inverted_false_positions
    ADD COLUMN IF NOT EXISTS signal_inverted BOOLEAN;

UPDATE laplace_inverted_false_positions
SET signal_inverted = FALSE
WHERE signal_inverted IS NULL OR signal_inverted IS DISTINCT FROM FALSE;

ALTER TABLE laplace_inverted_false_positions
    ALTER COLUMN signal_inverted SET DEFAULT FALSE,
    ALTER COLUMN signal_inverted SET NOT NULL;
