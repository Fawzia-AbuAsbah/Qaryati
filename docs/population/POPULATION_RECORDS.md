# Population Records

Population observations use scalar village/source IDs and the existing V1
`public.population_records` schema. Observation identity is the tuple
`(village_id, reference_year, methodology, source_id)`. Different sources may
report conflicting counts without overwriting each other's observations.

## Availability

All five service operations (create, list, get, update, delete) remain blocked
with HTTP 503 pending provider-owned authorization, draft visibility, village
and source validation, and transactional audit contracts. HTTP authentication,
CSRF and request validation can reject requests before they reach that boundary.
Repository tests demonstrate persistence behavior, not completion of CRUD or
authorization to use repository methods directly from another module.

New records are DRAFT. Entity update and deletion guards reject non-DRAFT
records; repository locking uses PESSIMISTIC_WRITE. Review transitions are not
implemented. Database constraints remain the final guard for foreign keys,
required fields, valid enum values, years 1800–2200, nonnegative counts,
date/year agreement, and composite uniqueness.

## Safe integration testing

Use Java 21 and Maven 3.9.11. Supply `DB_PASSWORD` only through the process
environment; never include its value in commands, files, or reports.

Before running any database test, independently connect explicitly to
`qaryati_test_db` and check `SELECT current_database()`. Confirm it is dedicated
to tests. Do not use `qaryati_db`, and do not interpret a verification flag as
evidence that the database was checked. Stop if credentials or isolation cannot
be verified. The test profile datasource must resolve to exactly
`jdbc:postgresql://localhost:5432/qaryati_test_db`, including any Flyway, Hikari
or JPA URL overrides.

Only after verification, run in PowerShell:

```powershell
./mvnw.cmd '-Dspring.profiles.active=test' '-Dspring.flyway.clean-disabled=true' '-Dqaryati.population-tests.database-verified=true' '-Dtest=com.qaryati.population.**.*Test' test
./mvnw.cmd '-Dspring.profiles.active=test' '-Dspring.flyway.clean-disabled=true' '-Dqaryati.population-tests.database-verified=true' '-Dqaryati.governorate-tests.database-verified=true' verify
```

The Governorate flag additionally records the existing Governorate requirement
to independently verify that this database exists and is dedicated to tests.
Keep Hibernate `ddl-auto=validate`, Flyway enabled and clean disabled. Never
use Flyway clean, schema recreation, or production/development database URLs.
The full suite also runs other members' existing tests without modifying them.

Population repository tests check the current database before inserting unique
fixtures. Fixture SQL creates only the minimum parent records required by
foreign keys, without importing other modules' entities or repositories. Each
test invocation is transactional and rolls back, including constraint failures;
identity sequences can still advance. Flyway may apply pending migrations
before transactional tests, so the dedicated test database remains mandatory.

Coverage includes persisted fields and timestamps, draft updates/deletes,
reference replacement, missing/locked lookup, nullability, foreign keys and
restricted parent deletion, boundary years/counts, invalid dates/enums,
defaults, composite uniqueness and existence queries, duplicate updates,
non-DRAFT guards, and persisted response mapping. Lock-mode assertions verify
the JPA lock request; they do not prove concurrent contention behavior.

Without database access, the following command runs only Population tests that
do not require PostgreSQL. This is not a substitute for integration testing:

```powershell
./mvnw.cmd '-Dspring.profiles.active=test' '-Dspring.flyway.clean-disabled=true' '-Dtest=com.qaryati.population.**.*Test,!com.qaryati.population.repository.PopulationRecordRepositoryIntegrationTest' test
```

## Verification on 2026-10-25

Java 21.0.12.1 and cached Maven 3.9.11 were used. The wrapper failed to start
(`Cannot index into a null array`), so the cached Maven executable was invoked
directly with an explicit local repository path. An initial Maven attempt also
failed before tests because its default repository resolved to `C:\.m2\repository`.

| Run | Tests | Failures | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Population excluding repository integration | 72 | 0 | 0 | 0 | SUCCESS |
| Maven verify excluding all repository integration classes and QaryatiApplicationTests | 134 | 0 | 0 | 0 | SUCCESS, executable JAR packaged |
| Final test-compile including added integration tests | — | — | — | — | SUCCESS |

The 72 Population invocations comprise controller 18, request validation 12,
entity 14, mapper 15, database guard 12, and service 1.

Excluded classes are not counted as JUnit skips: Population, Governorate,
AgriculturalProduct and User repository integration classes, plus the application
context test. Database integration and the unfiltered full verification suite
remain unrun. The agent process had no `DB_PASSWORD`; an explicit read-only
connection attempt to `qaryati_test_db` failed with `no password supplied`.
No verification flags were enabled and no database writes or migrations ran.
The test-profile datasource file names `qaryati_test_db`, but live database
identity and dedication could not be independently confirmed.
