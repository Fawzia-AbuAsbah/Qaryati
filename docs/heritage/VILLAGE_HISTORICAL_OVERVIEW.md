# Village Historical Overview Foundation

Implemented under `com.qaryati.heritage`, owned by Member 2, on
`Balsam/village-historical-overview`. V1 SQL is authoritative; proposed service
policies in the database contract still require team agreement.

## Database mapping and ownership

`VillageHistory` maps exactly the five columns of `public.village_histories`:

| Column | Mapping and contract |
|---|---|
| id | Long; database-generated identity primary key |
| village_id | Required scalar Long; unique FK to villages(id), ON DELETE RESTRICT |
| overview | Required TEXT; String without an application length limit |
| updated_at | Required TIMESTAMPTZ; Instant; SQL default now() |
| verification_status | Required VARCHAR(20); existing Heritage VerificationStatus; SQL default DRAFT |

A village has zero or one overview. No created_at, version, title, source_id,
archive or actor column is introduced. Village is not a cross-module JPA relation.
The unique constraint is the final protection against concurrent duplicate creation.

Sources belong to Data Quality. The existing `history_sources` table has composite
primary key `(village_history_id, source_id)`, an overview FK with ON DELETE CASCADE,
and a source FK with ON DELETE RESTRICT. Source records can be reused. There is no
claim_note column. No Source or HistorySource model, collection, DTO field or
production citation operation is introduced in Heritage.

## Foundation behavior

- Request and response records, handwritten component mapper and internal Spring
  Data repository follow Historical Events and Heritage Sites.
- Requests contain only villageId and overview. Village ID must be non-null;
  no positive-ID restriction is added. Overview must be nonblank on the DTO,
  entity and direct domain creation/revision paths. Nonblank is an application
  rule: SQL itself accepts empty and whitespace-only TEXT.
- Arabic, multiline and long text is preserved exactly without trimming.
- Creation always produces DRAFT. Existing statuses DRAFT, UNDER_REVIEW, VERIFIED,
  REJECTED and DISPUTED are reused and readable; no status transition is exposed.
- Protected constructor, field-access persistence and getters provide no public
  setters. Draft revision validates before assigning content, rejects village
  reassignment and every non-DRAFT status including null, and preserves identity,
  village and status. Null requests are rejected.
- `@PrePersist` and `@PreUpdate` maintain updatedAt using Instant.now(), following
  Population's lifecycle pattern. A new unpersisted draft has a null timestamp;
  revision changes content immediately and the timestamp changes on JPA flush.
  SQL defaults apply to SQL insertions that omit the timestamp. There is no SQL
  update trigger; direct SQL and bulk updates bypass lifecycle callbacks.
- Repository lookups are findByVillageId, findByIdAndVillageId and
  existsByVillageId. Per-village pagination is unnecessary. Existence checks are
  advisory and cannot prevent creation races. Repository access does not enforce
  authorization or visibility, and inherited deletion bypasses local draft guards.

## Database-free tests

Select only the following classes, using Java 21 and Maven:

```powershell
.\mvnw.cmd '-Dtest=com.qaryati.heritage.VillageHistoryTest,com.qaryati.heritage.HeritageArchitectureTest' test
```

VillageHistoryTest covers required/nonblank validation across DTO/entity/domain,
Arabic and long text preservation, default status, response mapping, managed
state preservation, every status and null guards, atomic failed revisions,
village immutability and lifecycle timestamp callbacks without sleeping.
The architecture checks inspect Heritage dependencies and run full Modulith
verification without starting an application context or connecting to a database.
Maven compiles integration test sources but does not execute them with this selection.

Verification in this implementation environment: all production and test sources
compiled with Java release 21 on JDK 22.0.1. The 15 VillageHistory test invocations
and both architecture checks passed: 17 total, zero failures, errors or skips.
Cached Maven 3.9.11 was used offline with an explicit local repository path after
the default repository path failed. The sandbox blocked compiler access to a
cached dependency; the approved run outside the sandbox succeeded.
An actual JDK 21 runtime check remains pending.

## Guarded PostgreSQL tests: not executed

VillageHistoryRepositoryIntegrationTest reuses Historical Events'
PersistenceConfiguration and DatabaseGuard, as Heritage Sites already does.
The guard requires the test profile, independent dedicated database verification,
the explicit heritage verification flag, the expected dedicated URL, safe override
settings and a live PostgreSQL/database/user identity check before Flyway/context
refresh. Flyway clean remains disabled and Hibernate validates rather than creates
the schema. There is no H2 fallback or automatic database creation.

Do not run this class or an unrestricted Maven test suite without separate
authorization and independent verification of a dedicated disposable test database.
No database connection, identity query, migration or integration test was executed
for this implementation. Compilation does not establish PostgreSQL correctness.

The authored suite covers full-field persistence, exact TEXT preservation, scoped
repository queries, uniqueness, required columns, FK/restricted deletion, status
checks/defaults, all statuses, JPA timestamp updates and direct SQL behavior.
Citation tests check duplicate links, both FKs, restricted source deletion and
link cascade with source retention using SQL fixtures only. These tests do not
introduce production cross-module dependencies or implement source management.

Future execution creates isolated generated fixtures and uses transaction rollback;
there is no cleanup SQL. Flyway initialization is outside rollback and identity
sequences may advance. Constraint tests assert PostgreSQL SQLSTATEs and issue no
further SQL after an error aborts a transaction. No test data was created here.

## Deferred integration and limitations

No controller, application service, endpoint, source association operation or
verification workflow is activated. Before CRUD integration, agree:

1. Village's provider-owned public identity/availability lookup, archive rules and
   concurrency semantics. The current Village API package is only a placeholder;
   an FK cannot establish application eligibility.
2. Security's trusted actor, action/role/village authorization, status visibility
   and consistent forbidden/not-found behavior. Existing authentication alone
   does not define overview permissions.
3. Data Quality public contracts for source eligibility, typed history_sources
   operations, transactional audit append and review/conflict recording. Existing
   SourceService and AuditLogService implementations do not establish a public
   Heritage-facing integration API. Agree the entity target naming and snapshots.
4. Verification transitions, reviewer permissions and the proposed requirement
   for at least one history_sources link before verification. Drafts may be
   uncited; SQL does not enforce source counts in any status. Coordinate verification
   and last-source removal with the same locking discipline and agree removal policy.
5. Atomic transaction/audit behavior, duplicate-create error translation, deletion
   and evidence retention, and lost-update prevention. V1 has no optimistic version
   column. Timestamp maintenance does not supply concurrency protection.

No permissive provider fallback or no-op audit is supplied. Foundation domain
guards do not authorize writes or protect direct SQL, bulk writes or inherited
repository deletes. Future services must coordinate these concerns before thin
controllers and application/security/concurrency tests are introduced.

The foundation adds eight files only. Existing modules, properties, migrations,
schema and shared test infrastructure remain unchanged. No Git commit, push,
merge or branch switch was performed. Implementation stops for user review.
