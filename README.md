# Crypto Market Scanner

## Supabase PostgreSQL configuration

Database-backed scanner persistence is enabled with the `db` or `manual-scanner-db` Spring profile. The default profile keeps datasource auto-configuration disabled so local scanner development and application-context startup do not fail when Supabase environment variables are absent.

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

If Supabase transaction pooling is used, the JDBC URL may differ from the direct connection or session pooler URL. Keep `sslmode=require` enabled for Supabase connections. When the `db` or `manual-scanner-db` profile is active without `SUPABASE_DB_URL`, the application falls back to `jdbc:postgresql://localhost:5432/postgres` for local PostgreSQL development rather than passing an unresolved placeholder to HikariCP.
