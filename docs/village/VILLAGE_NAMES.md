# Village Alternative & Historical Names

Feature 5 uses the existing public.village_names table without migrations.
Controllers delegate to a transactional service; record DTOs and a manual mapper keep
entities internal. No changes to global security or other modules are required.

## HTTP contract

Base: /api/v1/villages/{villageId}/names. Bearer authentication is required.

| Method | Suffix | Success | Roles |
| --- | --- | --- | --- |
| POST | none | 201, object | EDITOR, ADMIN |
| GET | none | 200, array ordered by id ASC | VIEWER, EDITOR, ADMIN |
| GET | /{nameId} | 200, object | VIEWER, EDITOR, ADMIN |
| PUT | /{nameId} | 200, object | EDITOR, ADMIN |
| DELETE | /{nameId} | 204, empty | ADMIN |

POST and full PUT accept:
```json
{"name":"Example historical name","languageCode":"en","nameType":"HISTORICAL","notes":null}
```

Object response:
```json
{"id":17,"villageId":41,"name":"Example historical name","languageCode":"en","nameType":"HISTORICAL","notes":null}
```

An existing village with no names returns []. No pagination or custom sorting is offered.
Individual lookups always use both villageId and nameId. A name under another village
returns 404. Missing villages return 404, including collection reads.

## Validation and duplicates

Path IDs must be positive Long values. name is required/nonblank, maximum 200 Java
string characters; languageCode is required/nonblank, maximum 12 Java string characters.
nameType is required and exactly ALTERNATIVE, HISTORICAL, FORMER or TRANSLITERATION.
notes is nullable TEXT with no additional API length cap.
PUT replaces all editable fields; omitted or null notes clears it. Unknown JSON fields,
including id and villageId even when null, are rejected. Parent association comes from
the path/service.

Submitted text is preserved without trimming, normalization or case folding.
There is no language allowlist, format regex, fuzzy matching or canonical-name comparison.
Duplicate checks use (villageId,name,languageCode,nameType), excluding the current ID on PUT.
The database unique constraint is the final integrity guard. Different villages, languages
or types may reuse text. Exact equality follows PostgreSQL column/database collation;
no special collation is declared in V1.

## Lifecycle, security and errors

All read roles may read names for archived villages and any village status.
Every mutation requires an unarchived DRAFT village. ADMIN is also subject to that rule.
DELETE physically removes a child row; repeating it returns 404. Village archive retains
names. Child writes never assign Village.updatedAt or change other parent fields.

| Status | Meaning |
| --- | --- |
| 400 | Invalid DTO, JSON, enum, path ID or unsupported request property |
| 401 | Missing or invalid Bearer authentication |
| 403 | Role does not permit the operation |
| 404 | Missing village or scoped name |
| 409 | Archived/non-DRAFT parent, duplicate tuple or sanitized integrity conflict |

For item mutations, positive IDs are checked, the parent is locked, the scoped child is
resolved, then lifecycle and duplicate checks precede mutation. Thus a missing child is
404 even for an archived parent. POST checks parent lifecycle before duplicate lookup.

Existing GlobalExceptionHandler formats handled errors with timestamp/status/error/message/path.
Framework parsing and security errors are status contracts only. SQL/driver details are
not returned for translated integrity failures.

## Transactions and limitations

Reads use @Transactional(readOnly=true); writes use @Transactional. Every write acquires
VillageRepository.findByIdForUpdate before accessing/mutating child state. The same
PESSIMISTIC_WRITE parent lookup is used by Village update/archive. Inserts, updates and
deletes flush inside the transaction. Integrity exceptions yield a generic 409 with no
retry; lock, connectivity and unrelated persistence exceptions are not labeled duplicates.

These locks serialize cooperating service mutations for one parent; they do not protect
every external writer or prove multi-connection behavior. No optimistic version token
exists, and later PUT requests can overwrite earlier changes. Listing is deterministic
for a stable dataset, without a cross-request snapshot guarantee. Large alias collections
may eventually require pagination.

V1 has identity PK, required RESTRICT village FK, required name VARCHAR(200),
language_code VARCHAR(12), checked name_type VARCHAR(24), nullable notes TEXT, and
UNIQUE(village_id,name,language_code,name_type). There is ix_names_village(village_id)
plus PK/unique backing indexes. There are no name timestamps, actors, archive/version
columns, effective dates or preferred-name flag. No such fields are invented.
Alias text is not added to existing canonical Village search by this feature.

## Verification

Feature 5 has 195 passing tests across two separate runs: 176 focused offline tests
and 19 PostgreSQL integration tests. Verified results and execution scope are recorded below.

Verified PostgreSQL coverage: all fields/types/identity and lazy mapping, null notes,
length and NOT NULL constraints, enum CHECK, tuple uniqueness and update conflicts,
allowed tuple variants and self-exclusion, scoped retrieval/list order, FK and parent
delete restriction, parent write-lock mode, full replacement, physical child deletion,
archived reads/name retention and unchanged parent updatedAt.

VillageNameRepositoryIntegrationTest reuses unchanged VillageTestDatabaseSupport:
test profile, exact dedicated test URL allowlist, explicit verification flag after
independent approval/identity/schema verification, per-connection PostgreSQL/database/user
checks, disabled Flyway and SQL initialization, Hibernate validation only,
@Transactional and @Rollback. Shared test properties enable Flyway, so the integration
class explicitly overrides it to false, matching existing Village tests.

Never run the integration class without separate authorization. Never target qaryati_db
or qaryati_governorate_dev. Tests do not initialize or migrate the schema. Fixtures join
the rollback transaction; identity sequences may still advance. After an expected SQL
failure, a test issues no further SQL. This suite does not prove real lock contention or
archive races across connections. The service in repository integration coverage runs
inside the test-owned guarded transaction; unit reflection checks transaction annotations.

DTO tests cover required fields, boundaries, enum values and text preservation; mapper
tests cover replacement and lazy parent IDs; service tests cover CRUD, lifecycle,
duplicates, scoped lookups, locking/delegation, IDs and sanitized errors; MVC tests use
production SecurityConfig and authority conversion with mocked JWT decoder, user lookup
and service. The MVC slice asserts absence of DataSource, EntityManagerFactory and Flyway.
Only a mock servlet context starts, with no application server or real JWT verification.
### Executed offline verification — 2026-11-04

Java 21 and locally cached Maven 3.9.11, offline mode. Final BUILD SUCCESS at
19:31:51 +02:00: 176 invocations, 0 failures, 0 errors, 0 skipped.

| Class | Passed |
| --- | --- |
| VillageNameRequestTest | 16 |
| VillageNameMapperTest | 2 |
| VillageNameServiceTest | 21 |
| VillageNameControllerTest | 78 |
| VillageTestDatabaseGuardTest (mocked JDBC only) | 58 |
| ModulithArchitectureTest | 1 |

Production sources (209) and test sources (78), including the new PostgreSQL integration
class, compiled. This offline selection excluded VillageNameRepositoryIntegrationTest;
its separately authorized successful PostgreSQL run is recorded below. No database
connection, SQL execution, Flyway, full application/server startup or unrestricted test
suite ran during offline verification. Only the database-free mock MVC servlet context started.

Exact test selection (using the cached Maven executable and JAVA_HOME set to Java 21):
```powershell
mvn -o '-Dtest=com.qaryati.village.dto.VillageNameRequestTest,com.qaryati.village.mapper.VillageNameMapperTest,com.qaryati.village.service.VillageNameServiceTest,com.qaryati.village.controller.VillageNameControllerTest,com.qaryati.village.repository.VillageTestDatabaseGuardTest,com.qaryati.ModulithArchitectureTest' test
```

The initial compile exposed a test-helper Runnable/Executable mismatch; the next run
exposed a Mockito restubbing error in an MVC error-response test. Both were corrected
before the final successful run. Expected notices included annotation processing,
existing unchecked test mocks, Mockito agent attachment and malformed-request warnings.
The offline run alone establishes neither live database compatibility nor lock contention.

### Verified PostgreSQL integration run - 2026-11-04

The user supplied the verified execution result: VillageNameRepositoryIntegrationTest
passed all 19 tests, with 0 failures, 0 errors and 0 skipped, on PostgreSQL 17.4 using
the dedicated qaryati_test_db. BUILD SUCCESS completed on November 4, 2026,
at 19:41:45 +02:00. No integration rerun was performed during the final review.

The earlier run failed one lock-mode assertion because its freshly inserted parent
retained Hibernate's internal WRITE mode, exposed through JPA as
PESSIMISTIC_FORCE_INCREMENT. The test-only correction clears the fixture persistence
context before invoking the service, then retrieves the managed parent for the unchanged
exact PESSIMISTIC_WRITE assertion. Production locking semantics were not changed.

The successful run covers mappings, PostgreSQL constraints and tuple uniqueness,
scoped queries and deterministic ordering, nullable-note replacement, archived reads and
name retention, physical child deletion, parent lock mode and parent timestamp preservation.
Archived/non-DRAFT write rejection and repeated DELETE are covered by the offline service
tests; this repository class does not establish service transaction-proxy behavior or
multi-connection lock contention.

| Separate verification run | Passed | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| Focused offline verification | 176 | 0 | 0 | 0 |
| PostgreSQL integration | 19 | 0 | 0 | 0 |
| Total across both runs | 195 | 0 | 0 | 0 |

Every future integration run still requires separate explicit authorization and independent
test database identity/schema verification before enabling the verification flag.
The exact localhost:5432/qaryati_test_db routing restriction, per-connection identity checks,
unchanged VillageTestDatabaseSupport guard, disabled Flyway/SQL initialization,
Hibernate validation-only configuration and transactional rollback protection remain in place.
The expected PostgreSQL user is the configured datasource username, checked against
current_user; the guard does not hardcode a role name.

Fixture row changes roll back. PostgreSQL IDENTITY sequences for governorates, villages
and village_names can advance despite rollback, including failed insert attempts.
No sequence reset is performed. No new schema, safety-guard or executable-code edits were
made during the final documentation review.

The integration Java file's old PREPARED ONLY header is stale historical commentary.
It was left unchanged because this review permits documentation edits only; the verified
execution record above is the current result.