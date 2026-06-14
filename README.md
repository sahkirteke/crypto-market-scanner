# Crypto Market Scanner

Crypto Market Scanner is a Spring Boot application for Binance Futures market scanning, scanner result persistence, paper-position workflows, and strategy analysis.

## Environment variables

Database credentials are read from environment variables:

- `SUPABASE_DB_URL`
- `SUPABASE_DB_USERNAME`
- `SUPABASE_DB_PASSWORD`

Example Windows PowerShell setup:

```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://aws-1-eu-central-1.pooler.supabase.com:6543/postgres?prepareThreshold=0&sslmode=require"
$env:SUPABASE_DB_USERNAME="postgres.wdsxcflkxixvwaqnltkk"
$env:SUPABASE_DB_PASSWORD="PASSWORD"
```

If Supabase transaction pooling is used, the JDBC URL may differ from the direct connection or session pooler URL. Keep `sslmode=require` enabled for Supabase connections.

## Final run instructions

### 1. Normal automated paper-trading run

Starts the API, connects to the configured database, and enables the default automated paper-trading workflow without requiring any Spring profile. The default runtime follows the 1H/4H scan schedules, persists scan results, attempts paper-position opens from `STRONG_LONG` / `STRONG_SHORT` entry signals after each successful scan, and evaluates open paper positions every five minutes.

```bash
mvn spring-boot:run
```

### 2. Scanner manual DB run

Runs a controlled scanner execution and persists the result.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-scanner-db
```

### 3. Paper open

Opens paper positions from the latest entry signals in a controlled manual run.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-paper-position
```

### 4. Exit evaluate

Evaluates open paper positions in a controlled manual run.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-exit-engine
```

### 5. Final smoke

Runs the final read-only smoke runner. It checks latest scan availability, latest candidates, latest signals, open paper positions, and analysis summary without opening or closing paper positions.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-final-smoke
```

## Safety notes

- Normal `mvn spring-boot:run` opens and evaluates paper trades automatically when scan signals qualify.
- Normal `mvn spring-boot:run` does not create real trades.
- Scheduler is enabled by default with `scanner.scheduler.enabled=true`.
- Paper automation is enabled by default with `scanner.paper-auto.enabled=true`.
- Paper opens are attempted automatically after successful scans when `scanner.paper-auto.open-after-scan=true`.
- Paper exit evaluation runs every five minutes by default when `scanner.paper-auto.evaluate-enabled=true`.
- Real Binance order/private API integration is not implemented.
- Safe mode is enabled by default with `scanner.safe-mode=true` for future live-order guardrails.

## API documentation

- Endpoint list: [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md)
- Smoke-test curl list: [`docs/SMOKE_TEST_CURLS.md`](docs/SMOKE_TEST_CURLS.md)

## V1.2.4 MULTI-TIMEFRAME ENTRY POLICY

1H timeframe is the entry trigger timeframe. 4H timeframe is used for direction, momentum, quality, risk classification, warnings, and entry priority scoring. 4H data is not a hard gate: AGAINST_4H_TREND candidates remain eligible, receive a major priority penalty, and cannot be classified as LOW risk.

Alignment definitions:

* `LONG_ALIGNED`: `close4h > ema20_4h` and `macdHist_4h > 0`.
* `LONG_STRONG_ALIGNED`: `close4h > ema20_4h`, `ema20_4h > ema50_4h`, `macdHist_4h > 0`, and `50 <= rsi14_4h <= 70`.
* `SHORT_ALIGNED`: `close4h < ema20_4h` and `macdHist_4h < 0`.
* `SHORT_STRONG_ALIGNED`: `close4h < ema20_4h`, `ema20_4h < ema50_4h`, `macdHist_4h < 0`, and `30 <= rsi14_4h <= 50`.
* `AGAINST_4H_TREND`: LONG with `close4h < ema20_4h`, or SHORT with `close4h > ema20_4h`.

The policy keeps hard gates limited to system/data safety conditions such as missing data, missing book ticker, high spread, market panic, in-position duplicate protection, position limits, scan entry limits, invalid quantity, and existing open position for the same symbol. RSI, funding, 24h price change, CHOP, weak volume, and 4H mismatch are handled as warnings, risk-level inputs, and entry-priority penalties instead of hard entry bans.
