# Historical Events CRUD

Implemented on Balsam/historical-events-crud-v2, with a clean working tree verified before edits.
The existing EventSourceLinks and HistoricalEventAuditSnapshot public APIs were verified before implementation.

Authenticated endpoints:

- POST /api/v1/historical-events returns 201 and Location.
- GET /api/v1/historical-events?page=0&size=20&villageId=1 returns a dedicated page envelope.
- GET /api/v1/historical-events/{id} returns 200.
- PUT /api/v1/historical-events/{id} returns 200.
- DELETE /api/v1/historical-events/{id} returns 204.

ADMIN and EDITOR can read every status and mutate DRAFT events. VIEWER can read VERIFIED events only.
Service methods enforce authentication and roles independently of HTTP security. Hidden details return 404.
List queries filter visibility and optional village before counting and pagination, with id ASC ordering.
Size must be between 1 and 100; page must be nonnegative. All supplied IDs must be positive.

Example create/update body:

```json
{
  "villageId": 1,
  "title": "Historical event",
  "description": "Event description",
  "datePrecision": "YEAR",
  "yearFrom": 1948,
  "sourceIds": [2, 3]
}
```

Existing EXACT/YEAR/PERIOD/UNKNOWN precision rules remain unchanged. Protected and unknown fields are rejected.
Every create/update requires an existing unarchived VERIFIED village and a nonempty list of existing source IDs.
Village reassignment is forbidden. Source duplicates are accepted and normalized by EventSourceLinks to distinct ascending IDs.
Responses include sourceIds. Source existence does not establish credibility or factual accuracy.
Reads and DRAFT deletion do not revalidate village eligibility.

Each mutation uses one writable REQUIRED transaction. Create flushes before linking. Update/delete lock the event
pessimistically and capture metadata plus existing sources before mutation. Delete relies on the existing FK cascade.
Audits use recordHistoricalEvent and immutable HistoricalEventAuditSnapshot values (excluding description).
Only the provider's documented NoSuchElementException is translated to a safe 404; infrastructure and audit failures propagate.
The controller advice returns safe 400/401/403/404/409 responses and a generic 500 for unexpected failures.

Validation selection (Java 21, database-free only):

```text
-Dtest=HistoricalEventTest,HistoricalEventServiceTest,HistoricalEventControllerTest,HistoricalEventTransactionTest,HeritageArchitectureTest test
```

Final result: 99 tests passed, with zero failures, errors, or skipped tests (Java 21.0.12.1).

Transaction tests exercise actual Spring transaction advice with a mocked transaction manager and verify rollback,
not commit, on audit failure for create/update/delete and link failure for create/update. They do not prove PostgreSQL
atomic rollback, FK cascade, or concurrent mutation behavior. HTTP tests use production bearer security and real service
with mocked persistence/public providers. Architecture tests verify Spring Modulith and restrict external module usage
to public API packages and the shared response envelope.

The existing guarded HistoricalEventRepositoryIntegrationTest was extended for visibility/counting/pagination and
the lock query. Its database guard and explicit opt-in remain intact. These database tests were NOT executed.

Remaining limitations:

- Village eligibility and source existence are point-in-time checks. VillageLookupApi provides no village lock;
  a concurrent village status/archive change can race with create/update. No new policy or cross-module locking is invented.
- A source removed after validation can cause a provider/FK failure. It propagates and must roll back the mutation;
  an unexpected FK failure returns generic 500 rather than being guessed to mean a missing source.
- Event update/delete acquire pessimistic row locks, but PostgreSQL lock scheduling, concurrent writers, deadlocks,
  and actual database rollback were not verified. Concurrent updates are serialized without an optimistic version/precondition;
  a later valid update may replace an earlier one. Concurrent reads and source-link reads are not a single repeatable-read snapshot.
- List responses currently resolve source IDs per event through the public API (bounded by page size).
- Approval/rejection, source credibility checks, verification workflows, and viewer submissions are outside scope.

No Population, Data Quality, Village, Security, migrations, schema, or other Heritage feature implementation was changed.
No commits, pushes, merges, branch switches, Docker, migrations, or database connections were performed.
