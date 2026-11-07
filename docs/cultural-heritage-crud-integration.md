# Cultural Heritage CRUD and Data Quality integration

## REST API and domain

All endpoints require authentication. The collection is `/api/v1/cultural-heritage`:

| Method | Route | Result |
| --- | --- | --- |
| POST | collection | 201, response body and Location |
| GET | collection | 200, page |
| GET | `/{id}` | 200, detail |
| PUT | `/{id}` | 200, updated detail |
| DELETE | `/{id}` | 204 |

POST/PUT body example:

```json
{"villageId":1,"name":"  Traditional craft  ","heritageType":"CRAFT","description":"  Original text\nSecond line  ","sourceIds":[3,2,3]}
```

Dedicated HTTP DTOs preserve the foundation DTO/entity/mapper contracts. Name is
nonnull and at most 200 characters; description is nonnull unrestricted TEXT.
Empty and whitespace-only names/descriptions remain valid. Text, spaces, multiline
content and Arabic are preserved without trimming or normalization. Categories are
exactly CRAFT, FOOD, TRADITION, PRACTICE, OTHER. There are no timestamps or version
columns. Unknown body fields, including id, verificationStatus, timestamps and
internal fields, are rejected, even when supplied as null.

Multiple records per village are allowed. Uniqueness is the exact stored tuple
`(village_id,name,heritage_type)`. Advisory checks cover POST and PUT, excluding
the current record on PUT. Database uniqueness is authoritative for racing writes.
Only Hibernate constraint violations with PostgreSQL SQLSTATE 23505 and constraint
`cultural_heritage_village_id_name_heritage_type_key` become duplicate 409 errors.
Other integrity failures propagate and roll back; they are not reported as duplicates.

Responses include id, villageId, name, heritageType, description,
verificationStatus and sorted distinct sourceIds. Pages contain content, page,
size, totalElements and totalPages. Optional filters are villageId (positive),
heritageType and verificationStatus. Defaults are page=0 and size=20; page must be
nonnegative and size must be 1–100. Ordering is fixed id ASC. Filtering, role
visibility, counting and pagination happen in the database. Source IDs for all
page records use one batch query, with no per-record source lookups. An empty page
requires no source SQL. ID ordering is stable for a stable dataset; it does not
provide a cross-request database snapshot.

## Authorization and new source policy

The service checks authentication and roles independently of the existing bearer
filter chain. ADMIN/EDITOR create and read every existing status, but update/delete
only DRAFT records. VIEWER reads only VERIFIED records. Unsupported roles have no
access. A status filter only narrows this visibility: VIEWER + any non-VERIFIED
filter returns an empty page and zero count; hidden details return 404.

New records are DRAFT. POST/PUT require an existing VERIFIED, unarchived village
through VillageLookupApi. Village association is immutable. Reads and authorized
DRAFT deletion do not revalidate current village eligibility. VillageLookupApi is
explicitly a point-in-time snapshot: eligibility can change before commit. Stronger
coordination would require a separate Village API/locking agreement.

Requiring at least one existing source on POST/PUT is a new Cultural Heritage
application policy, consistent with the Events/Overview CRUD references. It is not
a foundation invariant or SQL minimum-citation constraint. Existing uncited records
remain readable according to role/status visibility, and uncited DRAFT records
remain deletable. Their next PUT must supply sources. PUT replaces the complete
source set. Source-only updates are valid and create UPDATE audits; no updatedAt
is invented. Duplicate source IDs collapse. Null/empty lists and invalid IDs are
400; missing sources are 404. Existence does not establish evidential credibility.

## Public Data Quality integration

The existing `integration` named interface now also exposes
`CulturalHeritageSourceLinks` and `CulturalHeritageAuditSnapshot`.

The dedicated source contract provides sourceIds, sourceIdsByCulturalHeritageIds,
linkNewCulturalHeritage and replaceSources. Data Quality alone owns association SQL
against the existing cultural_heritage_sources table. Heritage uses only public
contracts. No Event/History/Site source interface is reused for cultural IDs.

IDs must be positive. Reads return immutable sorted distinct lists; batch maps are
also immutable. One LEFT JOIN query includes existing uncited parents with empty
lists and detects missing parents. Empty batches perform no SQL. Writes require
nonnull/nonempty sources, lock the Cultural Heritage parent row, validate every
source before deleting old links, and insert sorted distinct IDs. Initial linking
rejects already linked parents. Invalid arguments raise IllegalArgumentException;
missing parents/sources raise NoSuchElementException. Infrastructure failures
propagate. Parent deletion cascades links; source deletion remains restricted by
the existing FK. A source deletion race can still fail an FK after validation.

AuditWriter.recordCulturalHeritage uses stable target CULTURAL_HERITAGE and complete
typed snapshots: villageId, name, heritageType, description, sourceIds,
verificationStatus. Exact text is retained, including blanks and Arabic. Empty
snapshot source lists support legacy before states. CREATE has after only; UPDATE
has both; DELETE has before only. The existing enabled database actor lookup and
database audit timestamps remain unchanged. Previous Population/Event/History audit
contracts and behavior remain intact.

**Member 3 must review full-description storage in audit JSON.** Every update can
duplicate unrestricted description text in before/after JSON. Review access,
storage growth and retention expectations; descriptions are not redacted or
truncated by this feature. Audit timestamps describe operations, not entity updates.

## Transactions, locking and HTTP errors

Each mutation has an outer writable Spring transaction. POST flushes the entity
before SQL linking. PUT/DELETE lock the parent before checking DRAFT and capture
the complete before snapshot before changes. Source writes and auditing use
REQUIRED propagation and join that transaction. Audit/source failures propagate,
rolling back entity, links and audit together. DELETE snapshots precede cascading
link removal. Source-only and identical-value PUTs still append UPDATE audits.

Pessimistic locks serialize individual mutations. They do not detect stale client
payloads. Future verification/source writers must follow the same parent-lock
discipline; no verification workflow is introduced here.

| Status | Meaning |
| --- | --- |
| 400 | Invalid body/enum/IDs/pagination, protected fields, missing required sources |
| 401 | Missing/invalid authentication |
| 403 | Insufficient or unsupported role |
| 404 | Missing record/village/source, or hidden detail |
| 409 | Duplicate tuple, non-DRAFT mutation, village reassignment, ineligible village |
| 500 | Safe unexpected-error response; no SQL/provider/audit details |

Exception advice is scoped to CulturalHeritageCrudController. Common exception
handling and Village/Security/Population/Event/Site implementations are unchanged.

## Member 3 combined review checklist

- Public contract semantics and exposure in the integration named interface.
- Source SQL, batch LEFT JOIN behavior, immutable results, normalization, parent
  locks, reference validation order and REQUIRED transaction participation.
- Typed audit validation/serialization, exact text retention, stable target,
  before/after action shapes, actor resolution and propagated failures.
- Compatibility with existing Population, Event and Village History audit methods.
- Existing FK/cascade behavior, scoped unique-constraint translation and rollback.
- New feature tests and updated DataQualityIntegrationArchitectureTest allowlist;
  internal repositories/services remain unexposed.

## Verification and remaining PostgreSQL risks

Database-free selections cover foundation parity, categories/blank text, roles,
visibility query arguments/counts, filters, paging, source batching/normalization,
legacy uncited records, duplicates and scoped race translation, complete auditing,
source-only changes, Spring rollback requests, HTTP behavior and Modulith boundaries.
They also include existing Overview and Population/Event/History audit tests.
Transaction-manager doubles establish rollback requests, not actual database rollback.

Guarded suites CulturalHeritageCrudPostgresIntegrationTest and
CulturalHeritageSourceLinksPostgresIntegrationTest are authored but **not executed**.
They require QARYATI_CULTURAL_CRUD_TESTS=verified-test-db or
QARYATI_CULTURAL_LINK_TESTS=verified-test-db, profile test, the exact dedicated URL
`jdbc:postgresql://localhost:5432/qaryati_test_db`, and live database/user identity
verification before context refresh. Flyway and schema generation are forcibly
disabled. They require an independently prepared disposable schema; no H2 fallback
or automatic database creation is used. Some tests commit isolated fixtures for
cross-transaction checks and clean up those fixtures in finally blocks.

Coverage includes actual create/update uniqueness translation, filtered counts,
multiple records per village, source replacement/FKs/cascade, actual failure
rollback and concurrent update snapshots/row-lock contention. PostgreSQL runtime
behavior, constraint extraction, JPQL execution, lock timing and actual rollback
remain unverified until a separately authorized dedicated-database run. The older
foundation repository suite has a different guard and may enable Flyway; do not
include it or any unrestricted Maven suite in database-free verification.

No migration, category, timestamp, version, schema constraint, or unrelated module
change is required by this feature.

### Selected database-free run

Executed offline using Maven 3.9.11 and JBR OpenJDK 21.0.6. The exact selected
classes and invocation counts were:

| Test class | Tests |
| --- | ---: |
| CulturalHeritageTest | 17 |
| CulturalHeritageCrudServiceTest | 30 |
| CulturalHeritageCrudControllerTest | 42 |
| CulturalHeritageCrudTransactionTest | 1 |
| CulturalHeritageCrudDatabaseGuardTest | 2 |
| CulturalHeritageSourceLinksServiceTest | 8 |
| CulturalHeritageAuditTest | 4 |
| HeritageArchitectureTest | 2 |
| DataQualityIntegrationArchitectureTest | 1 |
| ModulithArchitectureTest | 1 |
| VillageHistoricalOverviewServiceTest | 16 |
| VillageHistoricalOverviewControllerTest | 30 |
| VillageHistoricalOverviewTransactionTest | 1 |
| VillageHistorySourceLinksServiceTest | 8 |
| VillageHistoryAuditTest | 3 |
| AuditWriterServiceTest | 9 |
| HistoricalEventAuditTest | 3 |
| **Total** | **178** |

Zero failures, errors or skips. Only these classes were selected with Maven
`-Dtest`; PostgreSQL suites were compiled but not executed. Existing Overview
implementation and tests were not modified. No database connection, Docker,
migration or broad test suite was run.
