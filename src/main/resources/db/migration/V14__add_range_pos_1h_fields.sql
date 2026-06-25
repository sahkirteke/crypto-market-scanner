ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS range_pos_1h NUMERIC(20,12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS last_closed_1h_low NUMERIC(30,12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS last_closed_1h_high NUMERIC(30,12);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS range_pos_1h_passed BOOLEAN;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS range_pos_1h_status VARCHAR(64);
