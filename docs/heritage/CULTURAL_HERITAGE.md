# Cultural Heritage Foundation

Implemented in `com.qaryati.heritage`, owned by Member 2. Flyway V1 is authoritative;
the database contract's proposed integration policies still require team agreement.

## Database mapping and validation

`CulturalHeritage` maps exactly the six columns of `public.cultural_heritage`:

| Column | Contract |
|---|---|
| id | Long; BIGINT generated-by-default identity primary key |
| village_id | Required scalar Long; FK to villages(id), ON DELETE RESTRICT |
| name | Required String; VARCHAR(200) |
| heritage_type | Required string enum; VARCHAR(25) |
| description | Required String; unrestricted TEXT |
| verification_status | Required existing Heritage VerificationStatus; VARCHAR(20); SQL default DRAFT |

Categories are exactly CRAFT, FOOD, TRADITION, PRACTICE, OTHER. All existing statuses
DRAFT, UNDER_REVIEW, VERIFIED, REJECTED, DISPUTED remain readable. Composite
uniqueness is `(village_id,name,heritage_type)` and is represented in JPA metadata.
V1 also declares `ix_cultural_village(village_id)`; primary and unique constraints
provide their backing indexes. Mapping metadata does not manage or change DDL.

Village ID, name, category and description are non-null on DTO, entity and direct
domain paths. Name has a maximum length of 200. Empty and whitespace-only name and
description are deliberately accepted, matching SQL and Heritage Sites' text policy.
Unlike Village Historical Overview's application nonblank rule, no additional
nonblank requirement is imposed here. Arabic, whitespace, multiline and long
descriptions are preserved without trimming. No positive-ID restriction is added;
the FK checks existence, while eligibility requires a future Village contract.

There are no timestamps, lifecycle timestamp callbacks, optimistic version,
coordinates, actor, archive or direct source_id fields. SQL does not prescribe
case folding or name normalization; duplicate checks use the stored name.

## Foundation behavior

Request and response records, a handwritten component mapper and an internal
Spring Data repository follow the existing Heritage foundations. Requests include
only villageId, name, heritageType and description. New entities are DRAFT.

The entity has a protected persistence constructor, getters and no public setters
or status transitions. Draft revision rejects null requests, village reassignment,
and every non-DRAFT status including null. All revision fields are validated before
any assignment; identity, village and status remain unchanged. The entity accepts
the request record following the existing Heritage pattern; this is local layer
coupling, not an external module dependency.

The repository supports village-scoped ID lookup, pagination ordered by unique ID
ascending, and duplicate checks including current-ID exclusion for future updates.
Ordering is deterministic for a stable dataset, without a cross-request snapshot.
Duplicate checks are advisory: only the database constraint prevents racing writes.
Repository access does not enforce authorization or visibility. Inherited deletion,
bulk operations and direct SQL bypass local draft guards.

## Sources and deferred integration

Data Quality owns sources and `cultural_heritage_sources`. Its composite primary
key is `(cultural_heritage_id,source_id)`, with a parent FK using ON DELETE CASCADE
and a source FK using ON DELETE RESTRICT. V1 declares
`ix_cultural_sources_source(source_id)`. Sources are reusable and multiple sources
can support one record. The join has no notes or timestamps.

No cross-module JPA association, citation model, source DTO field or production
source-link operation is introduced. SQL requires no minimum number of citations
even for VERIFIED records. At least one citation before verification is a proposed
service policy, not a schema guarantee.

No application service, controller, CRUD endpoint or verification workflow is
activated. Before those are introduced, agree:

1. Village's public identity/availability lookup, archive rules and concurrency
   semantics; its public API package currently remains a placeholder.
2. Security's trusted actor, role/action/village authorization, status visibility
   and consistent forbidden/not-found behavior.
3. Data Quality's public source lookup and typed citation operations, transactional
   append-only audit with actor snapshots, and verification/conflict contracts.
   Existing implementation services are not an exposed Heritage integration API.
4. Cultural target naming and existence validation. Existing verification creation
   explicitly supports only VILLAGE. Verification decisions and domain statuses
   are distinct vocabularies and must not be mapped implicitly.
5. Allowed transitions, reviewer permissions, citation minimums and last-source
   removal policy under a shared locking discipline. Coordinate status, review,
   source and audit changes atomically.
6. Duplicate conflict translation, retention/deletion rules, visibility-filtered
   queries, pagination limits and lost-update prevention without a V1 version column.

No permissive provider substitute or no-op audit is supplied. No other module,
properties, migration, schema or shared test infrastructure is modified.

## Database-free verification

Select only these database-free classes:

```powershell
.\mvnw.cmd '-Dtest=com.qaryati.heritage.CulturalHeritageTest,com.qaryati.heritage.HeritageArchitectureTest' test
```

Maven compiles all test sources, including guarded integration tests, before
executing only the selected classes. CulturalHeritageTest checks DTO/entity/domain
validation parity, text preservation, all categories, mapping, managed state,
atomic failed revisions, village immutability, status guards and request boundaries.
HeritageArchitectureTest checks Heritage dependencies and runs Modulith verification
without starting Spring or opening database connections.

Verification in the implementation environment: Maven 3.9.11 ran offline using
installed JDK 22.0.1 and Java release 21. No JDK 21 was found in the inspected Java
installation directory, so verification on an actual JDK 21 runtime remains pending.
All production and test sources compiled, including the guarded integration suite.
Only CulturalHeritageTest (17 invocations) and HeritageArchitectureTest (2) executed:
19 passed, zero failures, errors or skips. Both the Heritage dependency rule and
full Modulith verification passed. The initial sandbox attempt could not access
a cached dependency; the authorized execution outside the sandbox succeeded.

The guarded PostgreSQL suite was not executed. No database access or migrations
occurred; live persistence and SQL behavior remain unverified. Git whitespace
checks passed, including checks of the nine new files. Exactly the nine requested
files were added, with no existing tracked files changed. No commit, push, merge
or branch switch was performed.

## Guarded PostgreSQL tests: do not execute without separate authorization

CulturalHeritageRepositoryIntegrationTest reuses Historical Events'
PersistenceConfiguration and DatabaseGuard, matching the other Heritage suites.
The initializer requires the test profile, an independent dedicated database
verification flag, the expected dedicated JDBC URL, safe overrides and a live
PostgreSQL/database/user identity check before context refresh and Flyway.
Flyway clean is disabled, Hibernate validates, and JPA schema generation is disabled.
There is no H2 fallback or automatic database creation.

Do not execute this class or an unrestricted Maven suite without separate
authorization and independent verification of a dedicated disposable test database.
Never target the application database. Do not print credentials or commit secrets.
No database connection, identity query, migration or integration execution is
authorized as part of this foundation implementation.

The authored tests cover full-field round trips and revisions, every category and
status, SQL defaults, scoped pagination, advisory duplicate checks, composite
uniqueness including conflicting revision, required columns, name length, invalid
enums, FK integrity and restricted village deletion. Citation SQL fixtures check
both foreign keys, duplicate links, source retention, cascade, restricted source
deletion and many-to-many reuse; these do not implement production citation access.

Future execution uses generated fixture names and transaction rollback, without
cleanup SQL. Flyway initialization occurs outside rollback and identity sequences
can advance. Expected SQL failures assert SQLSTATEs and end database activity in
that transaction. Compilation alone does not establish PostgreSQL correctness.
