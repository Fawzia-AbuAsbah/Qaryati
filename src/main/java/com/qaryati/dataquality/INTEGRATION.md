# Historical Event source and audit integration

## Heritage Site integration

The same `integration` named interface now exports `HeritageSiteSourceLinks` and
`HeritageSiteAuditSnapshot`; no Heritage internals are imported or exposed.

```java
List<Long> HeritageSiteSourceLinks.sourceIds(Long heritageSiteId);
Map<Long, List<Long>> HeritageSiteSourceLinks.sourceIdsBySiteIds(Collection<Long> siteIds);
void HeritageSiteSourceLinks.linkNewSite(Long heritageSiteId, Collection<Long> sourceIds);
void HeritageSiteSourceLinks.replaceSources(Long heritageSiteId, Collection<Long> sourceIds);
void AuditWriter.recordHeritageSite(Long siteId, AuditAction action,
    HeritageSiteAuditSnapshot before, HeritageSiteAuditSnapshot after);
```

Site links use the existing `heritage_site_sources` table. The exception, transaction,
flush, authorization and DRAFT-check obligations described below for events also
apply to sites. Null/nonpositive IDs and null/empty write collections throw
`IllegalArgumentException`; missing sites/sources throw `NoSuchElementException`.
Relinking a site with existing links throws `IllegalStateException`. All sources
are checked through `SourceLookup` before deleting any association. Invalid IDs are
never silently discarded. Source existence implies neither credibility nor verification.
Parent deletion cascades links; source deletion is restricted by the existing FK.

Batch reads validate and deduplicate site IDs, then use one SQL LEFT JOIN, including
sites with no links. Any missing site fails the whole call. Empty input needs no SQL;
null input is invalid. Results are immutable maps in ascending site order with immutable,
sorted distinct source lists. No entities, repositories or cache cross the boundary.

The snapshot fields are `Long villageId`, `String name`, validated enum-name strings
`siteType` and `verificationStatus`, `String description`, `BigDecimal latitude`,
`BigDecimal longitude`, `String conditionNotes`, and `List<Long> sourceIds`.
Enum-name strings follow Historical Events conventions without importing Heritage enums.
Coordinates follow the model's paired, bounded six-decimal representation. Source lists
are copied, sorted and deduplicated; empty snapshots can represent existing unlinked sites.
Optional null fields are omitted from JSON. Description and condition notes are explicitly
allowlisted here; callers must not put credentials or secrets in these fields.

Audits use `HERITAGE_SITE`, existing actor lookup, database-generated timestamp and
JSON infrastructure. CREATE requires after only, UPDATE both, DELETE before only.
Capture a complete old snapshot and sources before replacement/deletion. All writes
use REQUIRED and join the caller's transaction; failures propagate. Standalone calls
start a transaction, so the owning service must group mutation, links and audit together.
This integration supplies no CRUD, approval or status-transition endpoints.

Source writes acquire `SELECT ... FOR UPDATE` on the site row before checking/changing
links and retain it until the caller transaction ends. This serializes cooperating
link writers; it does not coordinate earlier domain reads or prevent direct SQL bypasses.
Heritage must coordinate authorization and DRAFT checks with concurrent domain changes.
Single-site reads retain the event contract's separate existence/read observation limit;
batch reads use one statement snapshot. Concurrent source deletion can still cause an FK
exception after the existence check, and that failure must roll back the caller.

`HeritageSiteSourceLinksPostgresIntegrationTest` is guarded by
`QARYATI_SITE_LINK_TESTS=verified-test-db`, the test profile, exact localhost dedicated
database URL and live identity verification, following the event guard. It disables
DDL/migrations and rolls back fixtures. It covers persistence/replacement, batch SQL,
FK constraints/cascade/restrict, a parent-lock observation and rollback after linking
or real audit authentication failure. It has **not been executed**. The lock observation
checks PostgreSQL's parent relation lock; competing replacements, row-lock waiting,
deadlocks and end-to-end concurrent CRUD remain unverified. No database-level concurrency
claim follows from the database-free tests.

Public types are in `com.qaryati.dataquality.api`, named interface `integration`.
Implementations and persistence remain internal. No schema/migration changes.

## Signatures

```java
List<Long> EventSourceLinks.sourceIds(Long eventId);
void EventSourceLinks.linkNewEvent(Long eventId, Collection<Long> sourceIds);
void EventSourceLinks.replaceSources(Long eventId, Collection<Long> sourceIds);
void AuditWriter.recordHistoricalEvent(Long eventId, AuditAction action,
    HistoricalEventAuditSnapshot before, HistoricalEventAuditSnapshot after);
```

`HistoricalEventAuditSnapshot(Long villageId, String title, LocalDate eventDate,
Integer yearFrom, Integer yearTo, String datePrecision, List<Long> sourceIds,
String verificationStatus)` captures reviewed metadata. Precision and status use
validated enum names to avoid dependencies on Heritage internals. Null optional
fields are omitted, dates are ISO strings, and sources are copied, deduplicated
and sorted. Title is limited to 250 characters; never supply secrets. Description,
citation and arbitrary fields are deliberately not accepted. All accepted fields
are serialized. This snapshot is metadata, not a replacement for domain validation.

The existing Population `AuditSnapshot(Long, Long, Integer, Integer)` and
`AuditWriter.record(String, Long, AuditAction, AuditSnapshot, AuditSnapshot)` are unchanged.

## Caller sequence

Heritage must use one writable transaction for authorization, ownership and DRAFT
checks, persistence, links, and auditing. For creation, save and flush the event
before linking. For update, capture the old snapshot and links before replacement.
For deletion, capture the snapshot and links before deleting; the FK cascades links.
CREATE requires null before and nonnull after; UPDATE requires both; DELETE requires
nonnull before and null after. Audits use `HISTORICAL_EVENT` and the existing
authenticated, enabled actor lookup. Audit failures propagate.

All writes use REQUIRED propagation, never REQUIRES_NEW. A standalone call starts
its own transaction; callers must group the full mutation and propagate failures.
Source IDs and event IDs must be positive. Null/empty collections are rejected;
duplicates collapse to one association. Missing events/sources throw
`NoSuchElementException`; relinking an event with existing links throws
`IllegalStateException`. Source validation uses SourceLookup before deleting links.
Database/FK errors propagate and roll back; concurrent source deletion may therefore
produce a persistence exception rather than a lookup exception. Referenced sources
cannot be deleted. Missing-event reads fail, while existing events without links
return an immutable empty list.

## Concurrency limits

Writes obtain `SELECT ... FOR UPDATE` on the parent event before reading/changing
links. PostgreSQL holds the lock until the surrounding transaction ends. This
coordinates writers through this contract; it does not lock a Heritage entity
previously read by the caller, authorize mutation, or prevent direct SQL bypasses.
Heritage must coordinate its own state/authorization checks with concurrent changes.
Reads use separate existence/link queries and are point-in-time observations under
READ COMMITTED; concurrent deletion may yield an empty set after existence succeeds.
The delete/insert sequence is transactionally grouped, but concurrent PostgreSQL
behavior has not been executed/verified here. No general race-safety claim is made.

## Verification

Database-free tests exercise validation, duplicates, replacement ordering, SQL,
Spring transaction proxies, failures, snapshot serialization and Modulith exposure.
Existing Population audit/service/transaction tests are explicitly selected too.

`EventSourceLinksPostgresIntegrationTest` must not be included in routine selected
unit runs. It requires `QARYATI_EVENT_LINK_TESTS=verified-test-db`, the test profile,
the exact localhost `qaryati_test_db` URL, externally supplied credentials, and live
database identity verification before context refresh. It disables migrations and
DDL and requires an already migrated dedicated database. Fixtures roll back.
It covers persistence/replacement, duplicate and FK constraints, source deletion
restriction, cascade deletion, and enclosing transaction rollback. These tests were
added for later authorized execution, not executed for this PR.

Member 2 can build CRUD on these contracts after merge, with the caller obligations
above. PostgreSQL execution and concurrent end-to-end CRUD verification remain
outstanding before claiming database-level/concurrent behavior has been verified.
