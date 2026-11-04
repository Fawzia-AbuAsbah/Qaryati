# Village Management

## Scope and engineering choices

Member 1's Village module owns identity, geographic fields and administrative associations.
V1 uses the existing public.villages schema without migration changes. Controllers delegate
to transactional services; entities/repositories stay internal. VillagePageResponse is a
local immutable HTTP wrapper so Spring Data PageImpl internals cannot change the page JSON.
Feature 4 extends listing through service-built Specifications while preserving the page HTTP contract.

## HTTP API

Base path: /api/v1/villages. JSON requests/responses; authenticated Bearer token required.
Roles use Spring Security ROLE_ authorities.

| Method | Path | Success | Roles |
| --- | --- | --- | --- |
| POST | collection | 201, VillageResponse | EDITOR, ADMIN |
| GET | collection | 200, VillagePageResponse | VIEWER, EDITOR, ADMIN |
| GET | /{id} | 200, VillageResponse, including archived | VIEWER, EDITOR, ADMIN |
| PUT | /{id} | 200, VillageResponse | EDITOR, ADMIN |
| DELETE | /{id} | 204, empty; archive only | ADMIN |

Collection query parameters (all optional):

| Parameter | Default | Rules |
| --- | --- | --- |
| search | absent | Strip surrounding whitespace; blank means no filter; maximum 180 Java string characters after stripping |
| governorateId | absent | Positive Long; blank binds as absent |
| localAuthorityId | absent | Positive Long; blank binds as absent |
| status | absent | Exact DRAFT, UNDER_REVIEW, VERIFIED, REJECTED; blank means absent |
| sortBy | id | Exact allowlist: id, nameAr, nameEn, createdAt, updatedAt; blank means default |
| sortDir | asc | asc or desc, case-insensitive; blank means default |
| page | 0 | Zero-based nonnegative integer |
| size | 20 | Integer from 1 through 100 |

Invalid values return 400 without clamping. Offset above Integer.MAX_VALUE is rejected.
No parameters continues to return active rows ordered by id ASC, page 0, size 20.
Unknown query parameters remain ignored; they do not add supported filters.

Search matches a literal substring in nameAr OR case-insensitive nameEn. SQL LIKE
characters %, _ and the chosen escape character ! are escaped; a backslash is literal.
Arabic characters are matched as supplied, with no diacritic removal, letter unification,
transliteration or fuzzy matching. English uses LOWER with Locale.ROOT input folding;
database collation determines database case behavior and name ordering. Stored names are
unchanged. Null English names still allow Arabic matches.

All optional filters combine with AND and archived_at IS NULL is always applied.
Unknown positive parent IDs or unmatched combinations return an empty page, not 404;
filtering does not look up parents. All statuses are readable by every existing read role.
Sorting uses only allowlisted properties, with id ASC appended for non-ID sorts. Sorting
by ID uses only the requested direction. PostgreSQL defaults place null nameEn last for
ascending order and first for descending order. Offset pages are not a snapshot across
concurrent writes. Filtered counts and all eleven response fields remain unchanged.

Example: GET /api/v1/villages?search=Example&governorateId=2&status=VERIFIED&sortBy=nameEn&sortDir=desc&page=0&size=20

Implementation uses JpaSpecificationExecutor with a small private service Specification
builder and a bounded PageRequest. No fetch joins, parent lookups, new endpoints or schema
changes are needed. Existing fixed-ID repository listing remains available for compatibility.
Existing governorate/authority indexes and the active-row partial status index support
filters. Ordinary name B-tree indexes generally do not speed up LOWER(name) LIKE '%term%'.
Trigram or active composite indexes require measured evidence and a separately reviewed
new migration; no migration is part of Feature 4. Counts and deep offsets may be expensive.

POST and full PUT request (illustrative existing parent IDs):

```json
{
  "governorateId": 2,
  "localAuthorityId": 7,
  "nameAr": "قرية تجريبية",
  "nameEn": "Example Village",
  "nameOrigin": null,
  "locationDescription": "North of the governorate centre.",
  "latitude": 32.123456,
  "longitude": 35.234567,
  "elevationM": -10.25,
  "areaKm2": 12.345
}
```

POST, ID GET and PUT response example:

```json
{
  "id": 41,
  "governorateId": 2,
  "localAuthorityId": 7,
  "nameAr": "قرية تجريبية",
  "nameEn": "Example Village",
  "nameOrigin": null,
  "locationDescription": "North of the governorate centre.",
  "latitude": 32.123456,
  "longitude": 35.234567,
  "elevationM": -10.25,
  "areaKm2": 12.345,
  "status": "DRAFT",
  "createdAt": "2026-11-04T12:00:00.123456Z",
  "updatedAt": "2026-11-04T12:00:00.123456Z",
  "archivedAt": null
}
```

Exact top-level collection shape (content items use the complete response above):

```json
{
  "content": [],
  "number": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "numberOfElements": 0,
  "first": true,
  "last": true,
  "hasNext": false,
  "hasPrevious": false,
  "empty": true
}
```

These eleven fields are the explicit API contract; pageable/sort implementation objects
are not exposed. A nonempty first page uses number=0, first=true and hasPrevious=false;
last and hasNext describe whether later pages exist. A valid page beyond the result set
returns empty content while retaining its requested number/size and the total count.
DELETE has no response JSON; repeating it on an archived ID returns 204.

## Validation and relationships

| Request field | Rule |
| --- | --- |
| governorateId | Required positive Long; existing Governorate; immutable after creation |
| localAuthorityId | Optional positive Long; existing authority in the same Governorate |
| nameAr | Required nonblank, at most 180 characters |
| nameEn | Optional/null, at most 180 characters |
| nameOrigin | Optional/null TEXT; no additional API length cap |
| locationDescription | Required nonblank TEXT; no additional API length cap |
| latitude/longitude | Both null or both supplied; latitude ±90 and longitude ±180 inclusive |
| elevationM | Optional NUMERIC(9,2); negative values allowed |
| areaKm2 | Optional NUMERIC(12,3); strictly positive |

Coordinates fit NUMERIC(9,6). BigDecimal validation rejects values needing rounding or
exceeding storage capacity. Harmless trailing zeros and exactly representable scientific
notation are accepted. Elevation magnitude is at most 9,999,999.99; area at most
999,999,999.999. PostgreSQL itself can round excess fractional places; the API is stricter.

Names are stored unchanged, without trimming, normalization or case folding. Arabic-name
uniqueness is scoped to Governorate and includes archived rows. PUT excludes its own ID
from duplicate checks. Database uniqueness/FKs remain the final integrity guard.

PUT replaces all editable scalars: omitted optional fields clear, as does explicit null.
Omitted/null authority clears that association. Governorate must still be supplied and
cannot change. ID, status, createdAt, updatedAt and archivedAt are not accepted request
properties; unsupported JSON properties, including null, are rejected.

## Lifecycle, timestamps and errors

Creation explicitly assigns DRAFT and equal server-created createdAt/updatedAt. PUT permits
only active DRAFT rows, preserves status/identity/creation time, and refreshes updatedAt
even for an identical replacement. No submit/review/verify/reject/restore endpoints exist.
SQL supports UNDER_REVIEW, VERIFIED and REJECTED, but this API does not transition statuses.

DELETE archives any status and preserves all other fields and references. First archive
sets archivedAt and updatedAt together. Repeats do not read Clock, write or change timestamps.
PUT/archive acquire the same PESSIMISTIC_WRITE lookup within service transactions.
No physical deletion is called. The injectable UTC clock is truncated to microseconds;
timestamps need not strictly increase under clock changes or rapid writes.

| Status | Meaning |
| --- | --- |
| 400 | Invalid DTO, unknown JSON field, malformed JSON/body/ID/query, invalid pagination or authority/Governorate mismatch |
| 401 | Anonymous or invalid Bearer token |
| 403 | Authenticated user lacks an allowed role |
| 404 | Missing Village or required parent |
| 409 | Scoped duplicate, immutable Governorate change, archived/non-DRAFT edit, sanitized persistence integrity conflict |

For PUT: ID check, locked Village lookup, archive guard, status guard, requested Governorate
lookup, immutable Governorate check, authority lookup/match, uniqueness, then mutation/save.
A missing requested Governorate is 404; a different existing Governorate is 409.
Known save/flush integrity conflicts use a generic message without SQL details and no retry.
Lock/other unexpected persistence failures are not mislabeled as duplicates.

GlobalExceptionHandler formats validation/business errors with timestamp/status/error/message/path.
Malformed input and authentication/authorization responses are only established as status
contracts; this document does not promise identical bodies for every error category.
Production security/CSRF/JWT configuration is unchanged.

## Boundaries and remaining assignment requirements

Timestamps alone do NOT satisfy actor-aware who/when audit history. Audit integration,
actor retention and important-change history remain outstanding team work; no audit system
or schema was invented here. Feature 4 adds Village Search/filtering/flexible sorting. Alternate or historical Village
Names and Village Relations remain separate features. Source management
stays in Data Quality. Its current existence lookup includes archived IDs, so archive does
not prevent other modules from creating references. Public Village lookup integration
remains to be agreed with consumers. No full project-wide assignment compliance is claimed.

For API-Dog or an agreed equivalent: define the five operations, Bearer security scheme,
role matrix, VillageRequest/VillageResponse and the exact VillagePageResponse schema above;
include 201/200/204 examples and 400/401/403/404/409 examples. Store tokens only in local
secret variables, never exported artifacts. Publishing/importing a hosted API workspace
has not been performed; this Markdown is the reviewed local contract source.

## Verification evidence

Phase 4B established 249 database-free invocations: request 55, mapper 5, service 56,
MVC/security 133. Phase 5 adds exact wrapper schema/navigation assertions and output-capture
checks against the unstable PageImpl serialization warning. Current Phase 5 execution
results are recorded below after the focused run.

MVC uses a mock servlet slice with production security configuration/authority conversion;
JWT decoder, user lookup and VillageService are mocked. This is not full application startup,
real JWT verification or end-to-end persistence. Service locks are mocked; annotations alone
do not establish database concurrency. Architecture verification uses the existing
ModulithArchitectureTest.

Historical Village Management integration run (before Feature 4): VillageRepositoryIntegrationTest — 40 tests passed, 0 failures, 0 errors, 0 skipped, on the dedicated qaryati_test_db using PostgreSQL 17.4.
- VillageRepositoryIntegrationTest: full mappings/statuses, nullable parents, composite FK,
  scoped/archived uniqueness, nullability/length/CHECK constraints, numeric overflow and
  PostgreSQL rounding, microsecond timestamps, active page/count/ID order, archived lookup/
  write-lock mode, reference retention and parent delete restrictions.
- Every fixture joins a rollback transaction. Assertions after a failed SQL statement do
  not issue more SQL. Identity sequences can still advance despite rollback.
- Lock mode/query coverage does not prove contention or repeat-archive races across connections;
  those remain a separately reviewed concurrency-test extension.

VillageTestDatabaseSupport uses only jdbc:postgresql://localhost:5432/qaryati_test_db,
the test profile and qaryati.village-tests.database-verified=true after independent
approval/identity/schema verification. Never set that flag merely to bypass the guard.
It rejects shadowed/alternate routing and initialization configuration before constructing
the datasource. Every obtained connection checks PostgreSQL product, current_database()
and current_user before Hibernate validation or fixtures. Mismatches close the connection;
errors omit driver details. Alternate per-call credentials are prohibited.
The expected user is the independently approved configured test role.

Plain Spring persistence configuration disables Flyway and SQL initialization; Hibernate
only validates, never generates DDL. Existing schema must be verified separately; tests do
not prepare/migrate it. Do not run these tests until separately authorized. Never target
qaryati_db or qaryati_governorate_dev. No integration command was executed during Phase 5.
### Phase 5 focused run

Offline compilation and the explicit selection below succeeded: 309 invocations,
zero failures/errors/skips. Counts: MVC 134, request 55, mapper 5, service 56,
mocked database guard 58, Modulith architecture 1. Exact empty-page JSON, populated-page
metadata/navigation, and absence of the PageImpl serialization warning were asserted.
Only the mock MVC servlet context started; no full application/server or database ran.
Integration sources compiled but VillageRepositoryIntegrationTest did not execute.

```powershell
.\mvnw.cmd -o '-Dtest=com.qaryati.ModulithArchitectureTest,com.qaryati.village.dto.VillageRequestTest,com.qaryati.village.mapper.VillageMapperTest,com.qaryati.village.service.VillageServiceTest,com.qaryati.village.controller.VillageControllerTest,com.qaryati.village.repository.VillageTestDatabaseGuardTest' test
```

Nonfailure notices: compiler annotation processing, Mockito dynamic agent attachment,
and expected malformed-request resolver warnings. No unstable PageImpl warning occurred.
Guard checks use dummy values and mocked JDBC only; they establish guard behavior,
not live database identity, schema compatibility, or PostgreSQL test success.

## Feature 4 verification scope

Service unit and production-security MVC tests cover validation, literal search predicates,
filter intersection, allowlisted deterministic sorting, pagination and the existing JSON
contract. PostgreSQL integration scenarios exercise the production Specification through
the service and real repository under the existing guarded rollback transaction. They cover
Arabic/English and wildcard search, nullable names/authorities, statuses, filter intersection,
unknown parents, archived exclusion/counts, sorting/null ordering and page boundaries.
The complete VillageRepositoryIntegrationTest class, including these new scenarios, passed
67 tests in the approved PostgreSQL integration run recorded below.
The database guard, approval requirements, rollback protections and prohibited application
database targets documented above remain unchanged.

### Feature 4 offline verification (2026-11-04)

Production sources (201) and test sources (73), including the repository scenarios,
compiled successfully offline with Java 21. Only VillageServiceTest and VillageControllerTest
executed: 231 invocations, 0 failures, 0 errors, 0 skipped (85 service, 146 MVC/security).
The MVC slice asserts absence of DataSource, EntityManagerFactory and Flyway beans; only a
mock servlet context runs, with JWT/user/service dependencies mocked. This offline run
started no application server and performed no live database connection or migration.
The separate PostgreSQL integration run is recorded below.

Focused command (using the locally cached Maven 3.9.11 distribution):

```powershell
mvn -o '-Dtest=com.qaryati.village.service.VillageServiceTest,com.qaryati.village.controller.VillageControllerTest' test
```

The first run exposed eight Mockito assertion failures in newly added tests; those test
assertions were corrected and the final focused run passed. Nonfailure compiler/Mockito
notices include unchecked test mocking, annotation processing and dynamic agent attachment.

### Feature 4 PostgreSQL integration verification (2026-11-04)

VillageRepositoryIntegrationTest passed all 67 tests: 0 failures, 0 errors, 0 skipped.
BUILD SUCCESS completed at 2026-11-04 18:37:56 +02:00 on the approved dedicated
qaryati_test_db. The saved Surefire class report confirms the 67-test result; the approved
database and build completion time were supplied with the verified execution results.
Together with the focused offline run above, Feature 4 has 231 passing offline invocations
(85 service, 146 MVC/security) and 67 passing PostgreSQL integration invocations.

The integration class exercises existing mappings and integrity constraints plus the
Feature 4 production Specification: literal Arabic/English substring search and LIKE
escaping, active-only content and filtered counts, intersecting filters and all statuses,
unknown parents, nullable names/authorities, supported sorting with deterministic ID
tie-breaking, null ordering, pagination and empty/out-of-range pages. These results establish
the tested PostgreSQL behavior, not load performance or concurrent pagination snapshots.

Every rerun still requires explicit approval and independent test database identity/schema
verification before enabling qaryati.village-tests.database-verified=true. The exact
jdbc:postgresql://localhost:5432/qaryati_test_db allowlist, per-connection PostgreSQL database/configured-user identity
checks, test profile, transaction rollback protection, disabled Flyway/SQL initialization and
Hibernate validation-only settings remain unchanged. Never target qaryati_db or
qaryati_governorate_dev. Tests do not prepare or migrate the schema.

Fixture row changes roll back, but PostgreSQL IDENTITY sequences can advance despite
rollback, including failed insert attempts; gaps in generated IDs are expected. No sequence
reset is part of the tests. Lock-mode coverage does not prove contention or archive races.
