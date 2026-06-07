# Crypto Market Scanner

## Supabase PostgreSQL configuration

Database credentials are read from environment variables and must not be committed to `application.yml`.

Example Windows PowerShell setup:

```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://HOST:5432/postgres?sslmode=require"
$env:SUPABASE_DB_USERNAME="postgres"
$env:SUPABASE_DB_PASSWORD="PASSWORD"
```

Run the manual scanner persistence check with:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=manual-scanner-db
```

If Supabase transaction pooling is used, the JDBC URL may differ from the direct connection or session pooler URL. Keep `sslmode=require` enabled for Supabase connections.
