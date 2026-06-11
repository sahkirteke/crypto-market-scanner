ALTER TABLE paper_positions
    ADD COLUMN IF NOT EXISTS source_signal_side VARCHAR(16),
    ADD COLUMN IF NOT EXISTS execution_side VARCHAR(16),
    ADD COLUMN IF NOT EXISTS signal_inverted BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS inversion_reason VARCHAR(64);

UPDATE paper_positions
SET source_signal_side = COALESCE(source_signal_side, side),
    execution_side = COALESCE(execution_side, side),
    signal_inverted = COALESCE(signal_inverted, FALSE)
WHERE source_signal_side IS NULL OR execution_side IS NULL OR signal_inverted IS NULL;
