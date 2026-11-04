# Village Geographic Relations (Feature 6)

Uses the existing public.village_relations table, unchanged. No migration, global
security change, production configuration change or other-module change is required.

## HTTP contract

Base: /api/v1/villages/{villageId}/relations. Existing Bearer authentication applies.

| Method | Suffix | Success | Roles |
| --- | --- | --- | --- |
| POST | none | 201, object | EDITOR, ADMIN |
| GET | none | 200, array ordered by relation ID ASC | VIEWER, EDITOR, ADMIN |
| GET | /{relationId} | 200, object | VIEWER, EDITOR, ADMIN |
| PUT | /{relationId} | 200, object | EDITOR, ADMIN |
| DELETE | /{relationId} | 204, empty | ADMIN |

POST and full-replacement PUT accept:
~~~json
{"relatedVillageId":42,"relatedPlaceName":null,"relationType":"NEARBY","distanceKm":3.25}
~~~
Or use a free-text target:
~~~json
{"relatedVillageId":null,"relatedPlaceName":" Example place ","relationType":"OTHER","distanceKm":null}
~~~
Responses contain exactly id, villageId, relatedVillageId, relatedPlaceName,
relationType and distanceKm. Entities are internal; no invented timestamps or notes.

Relations are directed and outgoing from the path village. No reciprocal rows are
automatically created. Lists do not include incoming rows and have no pagination or
custom sorting. An existing source with no relations returns [].
Every item lookup uses both villageId and relationId; wrong-source items return 404.
PUT replaces target, type and distance; null/omitted distance clears it. Switching
between village and place targets clears the former target. DELETE physically removes
the relationship, and repeating it returns 404.

## Validation and duplicates

Path IDs and supplied target village IDs must be positive Long values.
JSON target IDs must be integer number tokens; fractional numbers and string IDs are rejected.
Exactly one target must be non-null. Supplied place names must be nonblank and at most
200 Java string characters. Text is preserved without trimming, normalization or case
folding. Source and referenced target must exist. Self-links are rejected.
Types are exactly NEARBY, ADJACENT, HISTORICALLY_RELATED and OTHER.
Distance is nullable BigDecimal, nonnegative, at most eight integer digits and two
fractional digits (maximum 99999999.99). Zero is valid. Excess precision is rejected
before persistence; PostgreSQL itself may round excess fractional scale.
No distance is calculated from coordinates and no type-specific distance rule exists.
Unknown JSON properties, including id and villageId even when null, are rejected.

Service duplicate identity is (villageId, relatedVillageId, relationType) for a village
target, or (villageId, relatedPlaceName, relationType) for a place target. PUT excludes
its current relation ID. Distance does not affect identity. Reverse links and different
relation types are allowed. Text equality follows PostgreSQL column/database collation;
V1 declares no special collation.

IMPORTANT: V1 has no relationship UNIQUE constraint. Service checks and source locks
protect cooperating service calls only. Arbitrary direct database writers can insert
duplicates. No database-wide duplicate guarantee or automatic cleanup is claimed.

## Lifecycle, security and errors

Reads permit archived sources/targets and any status. All mutations, including ADMIN
DELETE, require the SOURCE to be unarchived and DRAFT. Target status/archive state does
not restrict writes. Cross-governorate targets are permitted. Village archive retains
both incoming and outgoing rows. Relation writes never assign either Village.updatedAt.

| Status | Meaning |
| --- | --- |
| 400 | Invalid JSON, fields, enum, ID, target combination, self-link or distance |
| 401 | Missing or invalid Bearer authentication |
| 403 | Insufficient role |
| 404 | Missing source, specified target village or scoped relationship |
| 409 | Source archived/non-DRAFT, application duplicate or sanitized integrity conflict |

Item mutations validate IDs, lock the source, resolve the scoped child, check lifecycle,
then resolve the requested target and check duplicates. A missing child is therefore
404 even under an archived source. POST checks source lifecycle before target lookup.
Existing GlobalExceptionHandler formats handled errors with timestamp/status/error/
message/path. Framework parsing/security errors are status contracts only.

## Schema and transactions

V1 defines identity BIGINT PK; required RESTRICT source village FK; nullable RESTRICT
target village FK; nullable VARCHAR(200) place name; required checked VARCHAR(28) type;
nullable NUMERIC(10,2) distance with CHECK >= 0.
ck_relation_target requires exactly one non-null target. ck_no_self_relation rejects
equal source/target village IDs. SQL does not reject blank place names or duplicates.
Indexes: PK backing index, ix_relations_village(village_id),
ix_relations_target(related_village_id). There are no archive/version/audit columns.

Reads use @Transactional(readOnly=true). Writes use @Transactional and acquire the
existing VillageRepository.findByIdForUpdate source PESSIMISTIC_WRITE lock before
child/lifecycle/duplicate access. Village update/archive use the same parent lock.
Targets are looked up for existence without lifecycle checks or additional write locks.
Save and delete flush within the transaction. DataIntegrityViolationException becomes
a sanitized 409; the failed transaction is exited without retry or further queries.
Lock/connectivity/unrelated persistence failures propagate and are not called duplicates.

Cooperating source writes serialize. There is no optimistic version token; later PUTs
can overwrite earlier edits. List order is stable for a stable dataset, not a cross-
request snapshot guarantee. Multi-connection contention and archive races are not tested.

## Verification

Final focused offline run: BUILD SUCCESS on 2026-11-04 at 21:23:10 +02:00,
Java 21.0.12.1, cached Maven 3.9.11 with -o. 199 tests passed, zero failures,
errors or skips. All 217 production and 83 test sources compiled, including the
PostgreSQL integration class; that offline selection did not execute it.

| Class | Passed |
| --- | --- |
| VillageRelationRequestTest | 14 |
| VillageRelationMapperTest | 2 |
| VillageRelationServiceTest | 25 |
| VillageRelationControllerTest | 99 |
| VillageTestDatabaseGuardTest | 58 |
| ModulithArchitectureTest | 1 |
| Total | 199 |

Exact selection (using the cached Maven executable with JAVA_HOME set to Java 21):
~~~powershell
mvn -o '-Dtest=com.qaryati.village.dto.VillageRelationRequestTest,com.qaryati.village.mapper.VillageRelationMapperTest,com.qaryati.village.service.VillageRelationServiceTest,com.qaryati.village.controller.VillageRelationControllerTest,com.qaryati.village.repository.VillageTestDatabaseGuardTest,com.qaryati.ModulithArchitectureTest' test
~~~

The initial compilation found an incorrect EnumSource annotation in the new service
test. The next run caught numeric enum ordinals being accepted by Jackson. Both were
corrected; exact enum parsing follows VillageNameType. DTO-local ID deserialization
also rejects fractional, string and overflowing target IDs without global changes.
Final review added rejection of the validation helper's property name as unknown JSON
and independent CHECK-constraint integration scenarios; the final run includes these.
No unresolved compilation or offline-test failures remain.

During the offline run, no database connection or SQL execution, Flyway, migrations,
full application/server startup, unrestricted test suite, or PostgreSQL integration
execution occurred.
Expected compiler/Mockito notices and malformed-request MVC warnings were emitted.
Offline results do not establish live database compatibility or real lock contention.
The offline selection includes VillageRelationRequestTest, VillageRelationMapperTest,
VillageRelationServiceTest, VillageRelationControllerTest, unchanged
VillageTestDatabaseGuardTest (mocked JDBC only), and ModulithArchitectureTest.
MVC tests import production SecurityConfig and JWT authority conversion, with mocked
decoder, user lookup and service. They assert no DataSource, EntityManagerFactory or
Flyway beans. Only a mock servlet context starts, with no application server.

### Verified PostgreSQL integration run - 2026-11-04

The user supplied the verified execution result: VillageRelationRepositoryIntegrationTest
passed all 22 tests, with 0 failures, 0 errors and 0 skipped, on PostgreSQL 17.4 using
the dedicated qaryati_test_db. BUILD SUCCESS completed on 2026-11-04 at
21:29:34 +02:00. No integration rerun was performed during this documentation review.

| Separate verification run | Passed | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| Focused offline verification | 199 | 0 | 0 | 0 |
| PostgreSQL integration | 22 | 0 | 0 | 0 |
| Total across both runs | 221 | 0 | 0 | 0 |

These are two separate runs; the combined total does not describe one full-suite run.
The integration Java file's PREPARED ONLY header is stale historical commentary.
It remains unchanged because this review permits documentation edits only; the verified
execution record here is the current result.

The lock scenario flushes fixtures and clears the persistence context before the service
lookup, preserving the exact PESSIMISTIC_WRITE assertion without the fresh-fixture
Hibernate lock-mode issue. The passing suite establishes the guarded mapping/constraint
and single-transaction scenarios below; it does not establish multi-connection contention,
archive races or service transaction-proxy behavior.
It covers both target mappings, all types, lazy association IDs, distance null/zero/
boundaries and database rounding, outgoing order/scoping, reverse links/different types,
direct duplicate inserts, replacement, physical deletion, archived retention, parent
lock mode, unchanged parent timestamps, CHECK/NOT NULL/length/numeric overflow,
both FKs and both parent delete restrictions.

Future integration execution requires separate explicit authorization and independently
verified dedicated test identity/schema before enabling the verification flag.
Reuse unchanged VillageTestDatabaseSupport: exact test profile, exact
jdbc:postgresql://localhost:5432/qaryati_test_db URL, verification flag,
per-connection PostgreSQL/database/configured-user checks, Hibernate validate only,
Flyway disabled and SQL initialization disabled. Never target qaryati_db or
qaryati_governorate_dev. No schema initialization or migrations.
Fixtures use @Transactional/@Rollback; identity sequences may still advance and are
not reset. Tests issue no further SQL after an expected database statement failure.
The integration service is invoked within the guarded test-owned transaction, so this
does not independently prove production transaction-proxy or multi-connection behavior.
