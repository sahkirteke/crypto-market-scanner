# Laplace filter-state cut-over

`V23__add_laplace_market_retry_state.sql` intentionally does not clear legacy
`risky_entry_pending` rows. The previous version did not persist the rejection
reason on the pool row, so a genuine `RISKY_ENTRY_FILTER` block cannot be safely
distinguished from a market-filter block.

For a clean **paper-trading-only** reset, stop schedulers, archive the database,
record the validation boundary with `SELECT CURRENT_TIMESTAMP AS validation_started_at`,
and then run:

```sql
UPDATE laplace_coin_pool
SET risky_entry_pending = FALSE,
    pending_risky_raw_signal = NULL,
    pending_risky_signal_candle_close_time = NULL,
    market_retry_pending = FALSE,
    pending_market_raw_signal = NULL,
    pending_market_rejection_reason = NULL,
    pending_market_signal_candle_close_time = NULL;
```

Do not run this reset against state whose genuine risk blocks must be retained.
Measure new skip audits using only `laplace_trade_events.created_at >=` the recorded
`validation_started_at`; older audit events remain valid history but must not be
included in post-cut-over assertions.
