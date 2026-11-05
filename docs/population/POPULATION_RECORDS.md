# Population Records

Population observations use scalar village/source IDs and the existing V1
`public.population_records` schema. Observation identity is the tuple
`(village_id, reference_year, methodology, source_id)`. Different sources may
report conflicting counts without overwriting each other's observations.

## Availability

All five service operations are implemented at `/api/v1/population-records`.
Every service entry point checks authentication and roles before repository access.
ADMIN and EDITOR may create, read, replace and delete; VIEWER may read VERIFIED
records only. VERIFIED means published. ADMIN is not subject to an EDITOR-only
policy. User, role and administrator settings management is outside this module.

POST creates DRAFT records. PUT and DELETE require DRAFT and use the existing
pessimistic record lock. PUT cannot change villageId. POST/PUT require an existing,
unarchived VERIFIED village and an existing source, through public Village and
Data Quality APIs only. Reads and DRAFT deletions do not check current parent
eligibility. Source existence does not establish credibility: manual verification
of credibility and factual accuracy is required before future approval. Approval
by EDITOR or ADMIN will suffice for publication, but no approval, rejection or
conflict workflow is introduced here. Requests reject verification status, IDs,
timestamps and other unknown fields.

GET collection returns `{content, page, size, totalElements, totalPages}` with
defaults `page=0,size=20`, positive size at most 100 and nonnegative page. Sorting
is `referenceYear DESC, id ASC`. VIEWER status filtering happens in the repository
before pagination/counting; hidden detail returns 404. ADMIN/EDITOR read all
statuses. Offset pages are deterministic for a stable dataset, not a snapshot
across requests.

Mutations are transactional and reads are read-only. CREATE, UPDATE and DELETE
call AuditWriter in the mutation transaction; failures propagate and roll back.
Immutable AuditSnapshot values contain villageId, sourceId, referenceYear and
populationCount only. They omit methodology, referenceDate, notes, verification
status and timestamps, so this is not a complete field-level change history.

Village eligibility is a point-in-time snapshot: the public lookup acquires no
parent lock/version token. Archive/status changes can race with Population writes.
Population pessimistic locks serialize mutations but do not reject stale clients:
a later PUT can overwrite an earlier client's values. There is no optimistic
version, ETag or lost-update prevention guarantee. Source FK checks remain the
final existence guard when a source disappears after lookup.

Safe errors: 400 for invalid requests, IDs or pagination; 401 for absent/invalid
authentication; 403 for insufficient roles; 404 for missing references or hidden
records; 409 for duplicate observations, non-DRAFT mutations, immutable village
changes or ineligible villages; 500 for unexpected infrastructure errors without
internal details. Only SQLSTATE 23505 with exact constraint
`uq_population_observation` is translated from persistence to duplicate conflict.
Other integrity errors remain unexpected infrastructure failures.

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
./mvnw.cmd '-Dtest=PopulationRecordServiceTest,PopulationTransactionTest,PopulationRecordControllerTest,PopulationArchitectureTest,PopulationRecordTest,PopulationRecordMapperTest,PopulationRecordRequestValidationTest,PopulationTestDatabaseGuardTest' test
```

## CRUD integration verification, 2026-11-05

Final selected run: **98 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**
using Java 21.0.12.1 and cached Maven 3.9.11. The initial sandboxed compile could
not read a cached dependency; the selected tests were rerun with approved cache
access. Earlier test-double setup failures were corrected before the final run.

Use the explicit database-free selection above on JDK 21. Controller tests load
the production SecurityConfig and JwtAuthenticationConverterConfig with mocked
database user lookup and JWT decoder. They exercise stateless requests without
CSRF, missing/invalid bearer authentication, database roles and disabled users.
Service tests cover role boundaries, visibility, pagination, references,
immutability, DRAFT guards, duplicates, snapshots and propagated audit failures.
Transaction advice tests verify Spring selects rollback for audit failures on
each mutation and read-only transactions for both reads; they do not prove live
PostgreSQL rollback. PopulationArchitectureTest verifies Spring Modulith boundaries
and restricts Population dependencies to the public cross-module APIs and Common.

The repository integration extension checks VERIFIED filtering/counting and
ordered paging against PostgreSQL, under the existing pre-context database guard.
It is compiled but deliberately not executed for this change. No database
connections, Docker, migrations or broad suites are part of this verification.
The older verification report below is historical foundation evidence only.

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
