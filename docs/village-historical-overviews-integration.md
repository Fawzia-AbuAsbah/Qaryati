# Village Historical Overview CRUD and Data Quality integration

## Combined PR review: Member 3

Data Quality changes are additive public contracts in the existing `integration`
named interface. Review `VillageHistorySourceLinks`, `VillageHistoryAuditSnapshot`,
the new `AuditWriter.recordVillageHistory` method, its implementation, the internal
source-link repository/service, and their tests. Existing generic/Population and
Historical Events audit APIs remain unchanged. No migration or schema change.

`VillageHistorySourceLinks` supports individual and batch reads, initial linking,
and replacement. IDs must be positive. Writes require a nonnull, nonempty source
collection, deduplicate and sort it, lock the history row with `FOR UPDATE`, and
validate every source before inserting or deleting links. They use REQUIRED
transactions, joining the caller's transaction. Initial linking rejects an
already linked history with `IllegalStateException`. Invalid arguments produce
`IllegalArgumentException`; missing histories/sources produce `NoSuchElementException`.
Infrastructure failures propagate. An empty batch returns an empty map without SQL;
a nonempty batch uses one LEFT JOIN query, includes existing histories with no
sources, and rejects any missing history. Read results are immutable.

Only Data Quality accesses `history_sources`; Heritage uses the public API. The
existing history-delete cascade and source-delete restriction remain authoritative.
An existing source is a reference, not a determination of credibility. Source
deletion between validation and insertion can still fail the FK; this is an
infrastructure/integrity failure and rolls back the mutation, not a false duplicate.

The stable audit entity type is `VILLAGE_HISTORY`. Snapshots contain `villageId`,
the exact complete `overview`, sorted distinct `sourceIds`, and
`verificationStatus`. CREATE has only after, UPDATE both, DELETE only before.
Empty snapshot source lists support legacy records. The existing AuditLogService
resolves the authenticated enabled database actor and preserves existing audit
timestamp handling. No failures are swallowed. Full overview text is persisted
in audit JSON, so reviewers should account for audit storage size and retention.

`updatedAt` is deliberately excluded from the snapshot. The existing entity's
`@PrePersist`/`@PreUpdate` callbacks remain unchanged. An overview text change
causes an entity update and advances the timestamp at flush. A PUT that changes
only sources (or supplies identical values) does not dirty the history entity and
does **not** advance `updatedAt`. Source replacement still creates an UPDATE audit.
Audit timestamps therefore describe the audited operation independently of the
entity timestamp. No version table or approval workflow is introduced.

## HTTP contract

All five endpoints are under `/api/v1/village-historical-overviews`:

| Method | Path | Result |
| --- | --- | --- |
| POST | collection | 201 and Location header |
| GET | collection | paged response |
| GET | `/{id}` | overview detail |
| PUT | `/{id}` | updated overview |
| DELETE | `/{id}` | 204 |

POST and PUT body:

```json
{"villageId":1,"overview":"  Historical overview\nSecond line  ","sourceIds":[2,3]}
```

Unknown fields, including IDs, status and timestamps, are rejected. Text must be
nonblank and is never trimmed or normalized. The new HTTP DTOs are separate from
the existing foundation DTOs, which remain compatible with the domain mapper.
Responses include id, villageId, overview, updatedAt, verificationStatus and sourceIds.
Page responses contain content, page, size, totalElements and totalPages.

Authentication and roles are enforced by the service as well as the existing
security filter chain. ADMIN/EDITOR may create and read every status, but mutate
only DRAFT records. VIEWER reads only VERIFIED records; hidden details return 404.
Visibility and optional positive villageId filtering occur in the database before
pagination/counting. Default page is 0, size 20; size range is 1–100. Sort is id ASC.
Source lists for a page use one batch query rather than one query per history.

POST/PUT require an existing VERIFIED, unarchived village and existing sources.
Village reassignment is forbidden. Reads and authorized DRAFT deletion do not
revalidate village eligibility. Existing DRAFT histories without sources remain
readable/deletable, but their next PUT must provide sources.

Invalid requests yield 400, absent authentication 401, insufficient roles 403,
missing/hidden resources 404, duplicate village/non-DRAFT mutation/reassignment/
ineligible village 409. Unexpected failures return a scoped safe 500 without SQL
or provider details. The advisory duplicate check improves ordinary feedback;
database uniqueness is authoritative. Only Hibernate constraint violations with
SQLSTATE 23505 and constraint `village_histories_village_id_key` become duplicate
409 responses. Other integrity violations propagate and roll back.

POST flushes the new history before links. UPDATE/DELETE obtain a pessimistic
history lock and capture complete original source/text state before mutation.
Every write, source operation, and audit append shares the outer writable Spring
transaction; any failure rolls back the whole operation.

## Validation and remaining verification

Database-free selections cover domain behavior, roles, visibility queries,
pagination, batching, legacy source-less records, source validation and ordering,
typed audit payloads and actor resolution, Spring transaction rollback requests,
HTTP security/error/protected-field behavior, module boundaries, and compatibility
with existing audit contracts. Transaction-manager doubles verify rollback requests,
not actual database rollback.

Guarded PostgreSQL tests are added in `VillageHistoricalOverviewPostgresIntegrationTest`
and `VillageHistorySourceLinksPostgresIntegrationTest`. They require explicit
`QARYATI_HISTORY_CRUD_TESTS=verified-test-db` or
`QARYATI_HISTORY_LINK_TESTS=verified-test-db`, profile `test`, the exact dedicated
`jdbc:postgresql://localhost:5432/qaryati_test_db` URL and verified database/user
identity. Schema generation and Flyway are disabled. These tests cover authoritative
uniqueness/409, filtered pagination, source-only timestamps, real links/replacement/
cascade/FKs, rollback, and row-lock contention. They are **not executed** for this
implementation. PostgreSQL runtime behavior, SQL driver constraint extraction,
and lock timing remain unverified until a separately authorized guarded run.

No Docker, migrations, broad test suite, or database connection is needed or used
for the selected database-free tests. No Village, Security, Population, Historical
Events, Heritage Sites, or Common exception handler source changes are required.
