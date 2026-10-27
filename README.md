# Qaryati

Qaryati is a Java 21 / Spring Boot modular monolith backed by PostgreSQL.
Compose runs the entire backend and a separate PostgreSQL 17 database.

## Prerequisites

- Git and Docker Desktop with Linux containers and Docker Compose v2 enabled.
- On Windows, complete Docker Desktop's WSL 2 setup and start Docker Desktop.
- Internet access for base images, Maven 3.9.11, and Maven dependencies.
- Available loopback ports 8081 and 5433.

Clone the repository and open a terminal in its root. Host Java and Maven are
not needed for Docker builds. IntelliJ development still requires Java 21.

## Local environment

Copy the template (PowerShell):

```powershell
Copy-Item .env.example .env
```

Fill all four values in `.env` locally:

| Variable | Requirement |
| --- | --- |
| `DB_NAME` | A database name using letters, digits, and underscores. |
| `DB_USER` | A dedicated local database role using letters, digits, and underscores. |
| `DB_PASSWORD` | A unique randomly generated password. |
| `JWT_SECRET` | Standard Base64 encoding of at least 32 random bytes. |

Use a password manager or cryptographically secure generator. JWT configuration
decodes Base64 before checking key length; an arbitrary text password will not
work. To generate a JWT key directly into the copied `.env` without printing it,
run this once before filling in the other values (PowerShell):

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $jwtRng.GetBytes($jwtBytes) } finally { $jwtRng.Dispose() }
$envPath = Join-Path (Get-Location) '.env'
$envText = [System.IO.File]::ReadAllText($envPath)
if ($envText -notmatch '(?m)^JWT_SECRET=\r?$') { throw 'Expected one blank JWT_SECRET entry in .env' }
$envText = [regex]::Replace($envText, '(?m)^JWT_SECRET=\r?$', ('JWT_SECRET=' + [Convert]::ToBase64String($jwtBytes)))
[System.IO.File]::WriteAllText($envPath, $envText)
Remove-Variable jwtBytes, envText
```

Single-quote values containing `$` or `#` in `.env` to prevent interpolation or
comment parsing. Never commit `.env`, share it in GitHub issues, or include it
in screenshots. `.env.example` intentionally contains no credentials.
Compose reads `.env` automatically; Spring Boot launched from IntelliJ does not.
Existing shell variables override `.env`, so remove stale overrides from the
current shell before starting. Do not print environment dumps or expanded
Compose configuration; they can disclose credentials.

## Validate and start

The following are teammate startup instructions. Starting the application applies
pending Flyway migrations only to the Compose database. Run these commands only
when you intend to create/start this isolated stack:

```powershell
docker compose config --quiet
docker compose up --build -d
```

The Docker build uses the checked-in Maven wrapper and skips all test compilation
and execution. The runtime uses Java 21 as UID/GID 10001. No database access is
needed during image creation.

Compose requires all environment values to be nonempty. The application connects
to `jdbc:postgresql://db:5432/<DB_NAME>` on the Compose network. PostgreSQL's
health check gates application startup. Flyway is enabled, clean is disabled,
and Hibernate does not generate DDL. No test profile is activated.

## Verify

```powershell
docker compose ps
docker compose logs --tail=100 app
'SELECT version, success FROM public.flyway_schema_history ORDER BY installed_rank;' | docker compose exec -T db sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Expect the database to be healthy, the application to remain running, Spring Boot
startup completion in logs, and successful migrations 1 and 2. Review logs locally
and redact sensitive data before sharing them.

Open `http://127.0.0.1:8081`. Protected routes can return HTTP 401; this confirms
an HTTP response, not full application readiness. Security rules are unchanged.
Actuator health remains authenticated and no anonymous app health check is
configured. Use startup logs and the container database checks above to verify
startup. There is no default admin or seeded user; migration V2 seeds roles only.
Authenticated API verification requires an independently provisioned user.

## Persistence and shutdown

```powershell
docker compose stop
docker compose start
```

To remove this stack's containers/network while retaining database data:

```powershell
docker compose down
```

Never add `--volumes` or `-v`, prune volumes, or run Flyway clean. The
project-scoped `postgres_data` volume is mounted at `/var/lib/postgresql/data`.
No Windows PostgreSQL data directory is mounted or accessed. Keep the same
checkout directory/project name to reuse the same volume; changing the Compose
project name creates a separate stack and volume.

## IntelliJ and tests

The existing property files and local workflow are unchanged: IntelliJ can still
use Windows PostgreSQL on `localhost:5432/qaryati_db`, with `DB_PASSWORD` and
`JWT_SECRET` supplied in its run configuration. Compose uses host ports 5433 and
8081, leaving Windows PostgreSQL on 5432 and the usual local app port 8080 free.
Host database tools can connect to the Compose database at `127.0.0.1:5433` using
the credentials from `.env`.

The test profile remains `localhost:5432/qaryati_test_db`. Do not point tests at
the Compose application database or run a broad Maven test command as a Docker
verification step. Some tests start the full Spring context, apply migrations,
or require explicit database-verification guards. Follow the module-specific
documents under `docs/` for separately authorized integration testing.

## Troubleshooting and limits

- **Missing variable:** populate every entry in `.env`; use
  `docker compose config --quiet` to validate without printing expanded values.
- **Docker unavailable:** install/start Docker Desktop, enable Linux containers,
  and reopen the terminal if Docker is absent from PATH.
- **Port already allocated:** identify the listener on 8081 or 5433. Do not stop
  Windows PostgreSQL or change its port; deliberately select another Docker
  host binding if needed. Container database connections remain on `db:5432`.
- **Database healthy but app fails:** `pg_isready` checks server readiness, not
  credentials or migration success. Inspect local application logs for the cause.
- **Credentials changed after first startup:** PostgreSQL initialization variables
  do not update users/passwords in an existing volume. Restore the matching local
  configuration or plan an authorized credential rotation; do not delete data.
- **JWT startup failure:** check Base64 validity and decoded length of at least
  32 bytes without printing the key. Changing the key invalidates existing tokens.
- **Migration error:** preserve logs and data; do not edit applied migrations,
  enable baseline-on-migrate, or use clean as a workaround.
- **Database restart:** startup dependencies do not guarantee runtime recovery.
  Check app logs and deliberately restart only the Compose app if necessary.

This is a local team development setup. PostgreSQL's initialization role has
administrative privileges inside its isolated container. Environment values can
be read by users with Docker access; use a managed secret system and separate
migration/runtime roles for production.

Base images are constrained to Java 21 and PostgreSQL 17, but their tags receive
updates. Exact byte-for-byte image reproducibility requires reviewed digest pins;
the Maven wrapper distribution also currently has no configured SHA-256 checksum.
PostgreSQL 17 migration execution and a full container startup must be verified
separately before declaring runtime compatibility proven.
