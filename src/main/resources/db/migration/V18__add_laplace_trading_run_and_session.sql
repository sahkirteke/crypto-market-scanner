ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS trading_run_id VARCHAR(36);
ALTER TABLE laplace_paper_positions ADD COLUMN IF NOT EXISTS session_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_laplace_positions_run_status
    ON laplace_paper_positions(strategy, trading_run_id, status);
CREATE INDEX IF NOT EXISTS idx_laplace_positions_run_session_status
    ON laplace_paper_positions(strategy, trading_run_id, session_id, status);
