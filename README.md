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

### 1. Normal API run

Starts the API and connects to the configured database. Scheduler scans, automatic paper-position opens, exit evaluation, and analysis jobs are not started automatically in the default profile.

```bash
mvn spring-boot:run
```

### 2. Scanner manual DB run

Runs a controlled scanner execution and persists the result.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-scanner-db
```

### 3. Scheduler test

Triggers scheduler code through the manual scheduler profile.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-scheduler
```

### 4. Scheduler live profile

Enables scheduled scanner cron execution. Use only when automatic scheduler scans are intended.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=scheduler
```

### 5. Paper open

Opens paper positions from the latest entry signals in a controlled manual run.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-paper-position
```

### 6. Exit evaluate

Evaluates open paper positions in a controlled manual run.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-exit-engine
```

### 7. Final smoke

Runs the final read-only smoke runner. It checks latest scan availability, latest candidates, latest signals, open paper positions, and analysis summary without opening or closing paper positions.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-final-smoke
```

## Safety notes

- Normal `mvn spring-boot:run` does not open paper trades.
- Normal `mvn spring-boot:run` does not create real trades.
- Scheduler is disabled by default with `scanner.scheduler.enabled=false`.
- Paper opens happen only through the manual endpoint or `manual-paper-position` profile.
- Exit evaluation happens only through the manual endpoint or `manual-exit-engine` profile.
- Real Binance order/private API integration is not implemented.
- Safe mode is enabled by default with `scanner.safe-mode=true` for future live-order guardrails.

## API documentation

- Endpoint list: [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md)
- Smoke-test curl list: [`docs/SMOKE_TEST_CURLS.md`](docs/SMOKE_TEST_CURLS.md)
