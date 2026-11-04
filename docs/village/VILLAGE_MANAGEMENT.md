# Village Management

## Scope and engineering choices

Member 1's Village module owns identity, geographic fields and administrative associations.
V1 uses the existing public.villages schema without migration changes. Controllers delegate
to transactional services; entities/repositories stay internal. VillagePageResponse is a
local immutable HTTP wrapper so Spring Data PageImpl internals cannot change the page JSON.
Service and repository pagination remain unchanged.

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

Collection parameters: page defaults to 0, size to 20. Page is zero-based; size is 1–100.
Negative page, invalid size and offset above Integer.MAX_VALUE return 400. Values are
never clamped. Fixed ascending ID order comes from the repository, not client sorting.
No search/filter/sort parameters are implemented. Unknown query parameters are not a
supported filtering or sorting contract. Offset pages are not a snapshot across requests.

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
or schema was invented here. Advanced Village Search/filtering/flexible sorting, alternate
or historical Village Names and Village Relations are separate features. Source management
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

Verified integration run: VillageRepositoryIntegrationTest — 40 tests passed, 0 failures, 0 errors, 0 skipped, on the dedicated qaryati_test_db using PostgreSQL 17.4.
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
