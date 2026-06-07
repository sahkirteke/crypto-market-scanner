# Smoke Test Curls

```bash
curl http://localhost:8080/api/system/status
curl http://localhost:8080/api/scanner/latest
curl "http://localhost:8080/api/scanner/runs?limit=5"
curl http://localhost:8080/api/scanner/latest/candidates
curl http://localhost:8080/api/scanner/latest/signals
curl http://localhost:8080/api/paper/positions/open
curl "http://localhost:8080/api/paper/positions/closed?limit=20"
curl http://localhost:8080/api/paper/summary
curl http://localhost:8080/api/analysis/summary
curl http://localhost:8080/api/system/db-consistency
```
