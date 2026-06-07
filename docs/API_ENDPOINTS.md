# API Endpoints

## Scanner

- `GET /api/scanner/latest`
- `GET /api/scanner/runs?limit=5`
- `GET /api/scanner/runs/{id}`
- `GET /api/scanner/runs/{id}?includeEliminated=true`
- `GET /api/scanner/runs/{id}/coins?classification=WATCHLIST&limit=20`
- `GET /api/scanner/symbols/{symbol}/history?limit=10`
- `GET /api/scanner/latest/candidates`
- `GET /api/scanner/latest/signals`

## Paper

- `POST /api/paper/open-from-latest-signals`
- `GET /api/paper/positions/open`
- `GET /api/paper/positions/closed?limit=20`
- `GET /api/paper/positions/{id}`
- `GET /api/paper/positions/symbol/{symbol}?limit=10`
- `POST /api/paper/evaluate-open-positions`
- `POST /api/paper/positions/{id}/manual-close`
- `GET /api/paper/summary`

## Analysis

- `GET /api/analysis/summary`
- `GET /api/analysis/last?limit=100`
- `GET /api/analysis/range?start=...&end=...`

## System

- `GET /api/system/status`
- `GET /api/system/db-consistency`
