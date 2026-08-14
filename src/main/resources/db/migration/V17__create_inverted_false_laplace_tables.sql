-- Existing laplace_paper_positions, laplace_trade_events and laplace_trading_sessions remain the
-- lossless, backwards-compatible storage for INVERTED_TRUE.  INVERTED_FALSE is physically isolated.
CREATE TABLE laplace_inverted_false_positions (LIKE laplace_paper_positions INCLUDING ALL);
CREATE TABLE laplace_inverted_false_trade_events (LIKE laplace_trade_events INCLUDING ALL);
CREATE TABLE laplace_inverted_false_sessions (LIKE laplace_trading_sessions INCLUDING ALL);

CREATE INDEX idx_laplace_false_positions_strategy_status ON laplace_inverted_false_positions(strategy, status);
CREATE INDEX idx_laplace_false_positions_session ON laplace_inverted_false_positions(session_id);
