# Heritage Sites CRUD

All five endpoints require bearer authentication. ADMIN and EDITOR can create and read
all statuses, and update/delete DRAFT sites. VIEWER can read VERIFIED sites only;
hidden IDs return 404. Controller method security and service authorization both apply.
No approval or verification transitions are exposed.

| Method | Path | Success |
| --- | --- | --- |
| POST | `/api/v1/heritage-sites` | 201 and Location header |
| GET | `/api/v1/heritage-sites` | 200 page |
| GET | `/api/v1/heritage-sites/{id}` | 200 site |
| PUT | `/api/v1/heritage-sites/{id}` | 200 full replacement |
| DELETE | `/api/v1/heritage-sites/{id}` | 204 |

Create and update use the same body, for example:

```json
{
  "villageId": 1,
  "name": "Village spring",
  "siteType": "SPRING",
  "description": null,
  "latitude": 32.123456,
  "longitude": 35.123456,
  "conditionNotes": null,
  "sourceIds": [2, 3]
}
```

`villageId` must be positive and refer to an existing, unarchived VERIFIED village on
POST and PUT. PUT cannot change the original village. Reads and DELETE do not
revalidate the village. New records always start DRAFT; only DRAFT records can be
updated or deleted. There is no site-name uniqueness requirement.

`name` is required and limited to 250 characters. Empty and whitespace-only names
remain allowed, matching the original entity and schema policy; stricter name
validation is not proposed here. Description and condition notes remain nullable.
The six existing site types are ARCHAEOLOGICAL, TRADITIONAL_BUILDING, SPRING,
RELIGIOUS, VIEWPOINT and OTHER. Coordinates must both be present or both absent,
within latitude [-90,90] and longitude [-180,180], with at most six fractional digits.
Unknown fields, including IDs, verification status and timestamps, are rejected.

Every write requires at least one positive existing source ID. Duplicate IDs are
collapsed and sorted. Responses include immutable `sourceIds`. Only the public
`HeritageSiteSourceLinks` contract is used for associations. That contract exposes
no standalone source-existence validation method: CREATE flushes its new site,
then `linkNewSite` validates source existence in the same writable transaction.
An absent source produces 404 and rolls back the site. PUT uses `replaceSources`;
an empty replacement is rejected before persistence. Source credibility and approval
are separate concerns.

List parameters are `page=0`, `size=20` (1–100), and optional positive `villageId`.
Results use ID ASC ordering and the existing CRUD page convention:
`content`, `page`, `size`, `totalElements`, `totalPages`. Status and village filters
are applied in SQL before pagination/counting. Nonempty pages load sources in one
batch call; empty pages make no association calls.

Mutations each run in one writable Spring transaction. UPDATE and DELETE acquire
a pessimistic row lock before checking DRAFT status and capturing the old metadata
and source IDs. Audit snapshots include village, name, type, description,
coordinates, condition notes, sources and verification status. CREATE has an after
snapshot, UPDATE has before/after, and DELETE has before only. DELETE flushes the
site deletion and relies on the existing association FK cascade. Source/audit
failures propagate; infrastructure errors return a safe 500 response. Structured
400/401/403/404/409 errors follow existing CRUD conventions.

## Validation

The selected database-free tests are `HeritageSiteTest`, `HeritageSiteServiceTest`,
`HeritageSiteTransactionTest`, `HeritageSiteControllerTest`, and
`HeritageArchitectureTest`. The transaction test exercises Spring's transaction
interceptor and verifies rollback requests; it does not establish database rollback.
The controller test uses production security with provider doubles and no database.

`HeritageSiteCrudPostgresIntegrationTest` is compiled but must not be included in
the database-free test selection. It requires explicit
`QARYATI_SITE_CRUD_TESTS=verified-test-db`, the test profile, the existing
`qaryati.heritage-tests.database-verified=true` opt-in, dedicated
`jdbc:postgresql://localhost:5432/qaryati_test_db`, and independent database identity
verification. The database must already be migrated; this test disables Flyway and
uses schema validation. It exercises real JPA and the public source API, controlled
audit failures, source replacement, FK cascade, visibility-filtered pages, independent
connection lock timeout, and rollback. Village eligibility and audit append are
doubles in this test. Committed lock/rollback fixtures have explicit cleanup;
other fixtures roll back. These guarded tests were not executed during implementation.

## Remaining risks

Actual PostgreSQL lock contention, FK behavior and rollback remain unverified until
the guarded tests run. The village lookup is a point-in-time eligibility check:
the public village API exposes no lock spanning the mutation, so a concurrent
archive/status change may race with POST/PUT. Resolving that requires a separately
reviewed cross-module contract, outside this feature.

Site-row locks serialize cooperating site mutations. Callers of the source API must
honor its ownership/DRAFT preconditions; the API itself does not authorize them.
Concurrent source deletion after existence validation can still fail at the FK;
the failure propagates and rolls back, but may surface as safe 500 rather than 404.
Existing sources may be disputed or untrustworthy: existence is the only source
eligibility requirement here. Pages use offset pagination, so concurrent inserts,
deletions or visibility changes can alter later pages despite deterministic sorting.
Read-committed metadata and the subsequent source batch need not represent one
cross-query snapshot during concurrent changes. No stronger read-isolation contract
is claimed.
