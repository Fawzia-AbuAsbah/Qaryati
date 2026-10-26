# Heritage Sites & Landmarks Foundation

Implemented within `com.qaryati.heritage`. V1 SQL is authoritative; proposed
integration policies in the database contract still require agreement.

## Implementation and validation

- `HeritageSite` maps exactly the nine columns of `public.heritage_sites`.
  Identity is database generated; there are no timestamp or version columns.
  Village is a required scalar ID, with no cross-module entity association.
- `HeritageSiteType`: ARCHAEOLOGICAL, TRADITIONAL_BUILDING, SPRING, RELIGIOUS,
  VIEWPOINT, OTHER. String enum persistence matches V1.
- Request and response records and a dedicated mapper follow Historical Events.
  Requests omit ID and verification status. Creation always produces DRAFT.
  All five existing verification statuses can be read.
- Name is non-null, maximum 250 characters. Empty and whitespace names remain
  accepted because V1 has no nonblank constraint; no trimming is performed.
  Village ID and site type are required. No positive-ID restriction is invented.
- Description and condition notes are nullable, unrestricted TEXT.
- Coordinates are BigDecimal, optional as a pair, with inclusive latitude bounds
  -90..90 and longitude bounds -180..180. Jakarta field validation enforces three
  integer digits and at most six fractional digits, matching NUMERIC(9,6).
  A reusable Heritage-local class constraint checks pair presence on DTO and entity.
  Values with more than six fractional digits, including trailing zero digits,
  are rejected to avoid silent rounding. PostgreSQL itself rounds excess scale;
  the integration suite explicitly documents and tests that distinction.
- Domain draft creation/revision checks the same rules before assigning fields.
  Revision preserves ID, village and status, rejects village reassignment, and
  cannot partially mutate an entity after invalid input.
  Every non-DRAFT status (including null) blocks mutation.
- Internal repository offers village-scoped ID lookup and
  `findVillagePage(villageId, page, size)` ordered by unique ID ascending.
  Pagination is deterministic for a stable dataset; concurrent changes do not
  provide a cross-request snapshot. Internal repository access is not authorization.

No controller, application CRUD service, status transition operation, source link
operation or provider substitute is activated. Draft guards are local invariants;
they cannot protect direct SQL, bulk updates or inherited repository deletion.

## Database-free verification

Use Java 21 and Maven, selecting only database-free classes:

```powershell
.\mvnw.cmd '-Dtest=com.qaryati.heritage.HeritageSiteTest,com.qaryati.heritage.HistoricalEventTest,com.qaryati.heritage.HistoricalEventDatabaseGuardTest,com.qaryati.heritage.HeritageArchitectureTest' test
```

Maven compiles every test source, including integration tests, before executing
the selected classes. Unit coverage includes coordinate combinations, boundaries,
precision, nullability, required fields, all site types, mapper fields, managed
fields, atomic failed revision, village reassignment and draft guards.
The architecture test calls Spring Modulith `ApplicationModules.verify()` without
starting Spring or connecting to a database. Guard tests exercise the existing
shared Heritage test initializer's rejection paths without opening connections.

## Dedicated PostgreSQL integration verification

Do not execute an unrestricted Maven test command or either Heritage repository
integration class until independently verifying the dedicated test database.
Never target `qaryati_db`. The existing test profile uses
`jdbc:postgresql://localhost:5432/qaryati_test_db`, user `postgres`, and
`DB_PASSWORD`. Keep secrets out of tracked files and command output.

First verify with a read-only identity query, using securely supplied credentials:

```powershell
$env:PGPASSWORD = $env:DB_PASSWORD
try {
    psql -X -h localhost -p 5432 -U postgres -d qaryati_test_db -v ON_ERROR_STOP=1 -c 'SELECT current_database(), current_user, version();'
    if ($LASTEXITCODE -ne 0) { throw 'Database verification failed' }
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
```

Confirm PostgreSQL, database `qaryati_test_db`, user `postgres`, and operationally
confirm the database is dedicated to tests; its name alone is insufficient.
Only after that verification:

```powershell
.\mvnw.cmd '-Dtest=com.qaryati.heritage.HeritageSiteRepositoryIntegrationTest' '-Dspring.profiles.active=test' '-Dqaryati.heritage-tests.database-verified=true' test
```

The suite reuses the Historical Events persistence configuration and DatabaseGuard,
avoiding duplicated safety logic. Before context refresh/Flyway, the guard requires
the test profile, explicit verification flag and exact dedicated JDBC URL; rejects
alternative URL overrides; and checks the live database product, name and user.
The fixed DataSource and locked Flyway credentials use that verified connection.
Flyway clean is disabled, Hibernate only validates, and JPA DDL generation is disabled.
There is no H2 fallback or database creation.

Flyway may initialize the dedicated database outside test rollback. Test fixtures
use generated unique names and transaction rollback, with no cleanup SQL. Identity
sequences can advance. Village/governorate SQL is test setup only, not production
cross-module access. Constraint tests bypass Bean Validation using JDBC, assert
SQLSTATEs and issue no further SQL after PostgreSQL aborts a transaction.

Integration coverage includes all types/statuses, full-field round trips, draft
revision and nullable content, coordinate pairs/ranges, numeric overflow and SQL
rounding, required columns, name length, unknown enums, FK integrity, restricted
parent deletion, status default, duplicate names and deterministic village pagination.

## Pending integration contracts and limitations

Before activating public operations, agree provider-owned public APIs and policies:

1. Security: trusted actor identity, role/action authorization, village scope,
   status-based visibility and consistent forbidden/not-found behavior.
2. Village: identity/availability lookup, archive eligibility and concurrency semantics.
   An FK establishes existence, not application eligibility.
3. Data Quality: source eligibility and typed `heritage_site_sources` operations,
   transactional append-only audit with actor snapshots and before/after values,
   verification/conflict recording and target naming.
4. Verification: permitted transitions and reviewer permissions, proposed minimum
   citation requirement, last-source removal behavior and shared locking discipline.
5. Application operations: endpoint contracts, draft deletion/retention rules,
   visibility-filtered queries, pagination limits and lost-update prevention.
   V1 provides no optimistic version column.

Existing provider implementation classes do not by themselves establish an approved
Heritage integration contract. Future transactional services must coordinate these
contracts before thin controllers or production writes are enabled.

## Architecture and quality review

Production dependencies stay within Heritage and framework APIs. Entity invariants,
DTO validation, mapping and repository persistence are separated; the stateless
mapper requires no injected collaborators. Existing VerificationStatus and test
database safety infrastructure are reused. No shared Common, other module,
migration or database-contract file is changed.

The entity accepts the request record to match the existing Historical Events
foundation. This is local layer coupling; a future domain command/value object
could separate transport concerns if application orchestration grows. No service
or dependency injection abstraction is added without a current responsibility.
Cross-layer validation deliberately protects both Bean Validation callers and
direct domain callers; parity is tested on DTO, entity and domain construction.

### Verification results in the implementation environment

All production and test sources compiled with Java release 21 using installed JDK
22.0.1 and cached Maven 3.9.11 offline. An actual JDK 21 runtime remains a local
verification step. The initial sandbox compilation was blocked by access to a
cached dependency; the authorized run outside the sandbox compiled successfully.

Final database-free execution: 96 test invocations, 95 passed, zero assertion
failures, one error (the global Modulith verification), zero skipped. This includes
36 new HeritageSite unit cases, 54 HistoricalEvent regression cases, four database
guard cases and the passing Heritage-only production dependency rule.

The full Spring Modulith verification fails on existing out-of-scope dependencies:
Data Quality's AuditLogService, DataConflictService and VerificationRecordService
access Security User/UserRepository internals; Population uses non-exposed Common
ApiErrorResponse/DuplicateResourceException; Village GovernorateService uses
non-exposed Common exception types. None of the reported violations involve
Heritage. These findings are retained as a failing architecture test, not suppressed.
Fixing them requires the relevant owners' API exposure and dependency changes.

The 34 new PostgreSQL integration test invocations were not executed:
DB_PASSWORD was unavailable and the dedicated database could not be independently
verified. No database-writing tests or migrations were run. SQL behavior and
persistence mapping remain pending live PostgreSQL verification.

The foundation is ready for review and dedicated local database verification.
Commit readiness remains conditional on user review and acceptance of the pending
PostgreSQL/JDK 21 verification and existing global architecture findings. No Git
mutation, commit, push, merge or branch switch was performed.
