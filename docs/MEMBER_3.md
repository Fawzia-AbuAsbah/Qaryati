# Member 3 delivery and API guide

Verified on 2026-10-22. Branch: `roaa/audit-logs`. No commits, pushes, branch changes, merges, or history changes were made.

## Audit and implementation decisions

The repository was inspected before changes: Maven configuration, application/test properties, V1/V2 migrations, implemented security/data-quality layers, tests, architecture, and database contract. Other business modules currently contain package skeletons; no Postman collection exists.

The initial implementation already provided BCrypt login, issuer/signature/time validation, database-backed JWT roles and enabled-account checks, conflict workflow, administrator audit queries, source creation/read/search/filter services, village-source links, and transactional audit call sites. Gaps were source PUT/URL validation, service protection on source/link operations, required JWT claims, bounded throttling, and missing regression evidence. Additional defects found during work were rejection of the supported NEEDS_REVISION decision and null generated timestamps in newly returned entities.

Implementation sequence: preserve the existing contracts; add source PUT and validation; enforce the existing permission policy in services; derive reviewers from SecurityContext; strengthen JWT validation and login throttling; repair verification/timestamp behavior; add targeted tests; replay migrations and run clean tests; document remaining dependencies. Existing migrations and dependencies were preserved.

## Requirement checklist

| Requirement | Status | Evidence or limit |
|---|---|---|
| Existing architecture, packages, branch and migration preservation | COMPLETE | No reorganization or migration edits; diff review |
| User persistence and role/repository operations | COMPLETE | Existing user/role entities, unique/FK constraints, repository tests |
| POST login, BCrypt, correct and invalid credentials | COMPLETE | Actual login integration tests, generic failures |
| JWT creation, signature, issuer, expiration, required subject/expiration | COMPLETE | JwtServiceTest; subject and expiration now mandatory |
| Missing/disabled users and malformed/expired tokens | COMPLETE | JWT unit tests plus real HTTP missing/disabled/malformed tests |
| Authenticated identity and stateless API | COMPLETE | Database-resolved subject; reviewer and actor from SecurityContext; session test |
| External secrets and generic authentication errors | COMPLETE | DB_PASSWORD/JWT_SECRET placeholders; no production credentials added |
| Role policy, 401/403, sensitive service methods | COMPLETE | Existing role/endpoint tests plus direct source/link service-denial tests |
| Source create/read/update, missing IDs, validation | COMPLETE | 21 source controller integration cases; PUT reuses identical create field contract |
| Source title search/type filtering | COMPLETE | Existing service methods and repository tests preserved |
| Conflict supported type/reference/value validation | COMPLETE | Only VILLAGE; existing/missing target, unsupported type and value-count tests |
| Conflict transitions, reviewer, notes, audit | COMPLETE | OPEN -> IN_REVIEW -> RESOLVED; invalid/repeated transitions, disabled reviewer and audit tests |
| Transaction-safe conflict/source transitions | COMPLETE | Pessimistic row locks serialize mutations; no schema change |
| Audit actor/action/entity/time/before-after metadata | COMPLETE | Actual source/link/conflict/verification operation tests; generated timestamp mappings |
| Audit restrictions and atomic persistence | COMPLETE | ADMIN reads; no write endpoints; failed audit rolls back business creation |
| Audit sensitive metadata minimization | COMPLETE | Explicit snapshots omit conflict values, resolution notes, source URLs and citations |
| Audit coverage of implemented business mutations | COMPLETE | Source create/update, village link create, conflict create/review/resolve, verification create |
| Audit integration for other members' modules | PARTIAL | No business services exist in those modules yet; cannot integrate or test their mutations |
| Village-to-source API and referential integrity | COMPLETE | Existing composite PK and FK validation/tests retained |
| Population/heritage source associations | PARTIAL | Six source FK relationships verified; owning Java modules/APIs are still skeletons |
| Verification-to-source relationship | COMPLETE | No such relationship required by current contract; no speculative table/API added |
| Five login requests/IP/minute and 429 | COMPLETE | Filter counts valid, invalid and malformed requests; Retry-After header |
| Rate reset, concurrency, cleanup and bounded memory | COMPLETE | Fake clock, simultaneous same/new client tests, capacity/reclamation tests |
| Spoofed forwarding headers | COMPLETE | Socket peer used; forwarded-header processing disabled by default; integration test |
| Full Maven test suite and fresh migration replay | COMPLETE | 134 tests, 0 failures/errors/skipped; fresh PostgreSQL 18.6 database |
| PostgreSQL 17-specific verification | PARTIAL | Version 17 server binaries unavailable; retained guarded SQL script was not run |
| API/environment/test documentation | COMPLETE | This guide; no existing Postman collection to update |

## Run configuration

Requires Java 21, Maven wrapper, and PostgreSQL. Existing defaults use `localhost:5432`, username `postgres`, database `qaryati_db`; test profile uses `qaryati_test_db`.

Set `DB_PASSWORD` and `JWT_SECRET` through your environment or secret manager. JWT_SECRET must be Base64 encoding of at least 32 random bytes. Do not store production values in Git or request logs. Optional standard overrides are `SPRING_DATASOURCE_URL` and `SPRING_DATASOURCE_USERNAME`. Default token lifetime is 15 minutes (`APP_JWT_EXPIRATION_MINUTES` overrides it). Issuer is currently fixed to `https://qaryati.example` in both encoder and validator. Timestamp validation uses Spring Security's default clock-skew tolerance.

Flyway owns schema changes; V1 creates the schema and V2 seeds role definitions, not login accounts. Provision enabled users with BCrypt password hashes and assigned roles using the team's existing database administration workflow. No public registration/user-management API is supplied.

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd clean test
```

Configure the datasource to a dedicated test database before testing: the context smoke test uses the default profile, while integration tests activate `test`. Setting SPRING_DATASOURCE_URL explicitly ensures both target the same test database. Never point test execution at production.

## Authentication and permissions

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"<existing-username>","password":"<password>"}
```

Successful response (200):

```json
{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":900}
```

Supply `Authorization: Bearer <JWT>` on protected requests. GET `/api/v1/auth/me` returns `{"username":"<authenticated-username>"}`. Tokens are HS256 signed, issuer/time/subject validated, and users/roles reloaded from the database for each bearer authentication. Disabled or missing users cannot continue using an existing token. REST requests do not create authentication sessions.

| Operation | Permission |
|---|---|
| POST `/api/v1/auth/login` | Public, rate limited |
| GET `/api/v1/auth/me` | Authenticated |
| GET `/api/v1/sources`, `/api/v1/sources/{id}` | VIEWER, EDITOR, ADMIN |
| POST `/api/v1/sources`, PUT `/api/v1/sources/{id}` | EDITOR, ADMIN |
| POST `/api/v1/villages/{villageId}/sources` | EDITOR, ADMIN |
| GET `/api/v1/villages/{villageId}/sources`, `/api/v1/sources/{sourceId}/villages` | VIEWER, EDITOR, ADMIN |
| POST `/api/v1/data-conflicts` | EDITOR, ADMIN |
| GET `/api/v1/data-conflicts?entityType=VILLAGE&entityId={id}` | Authenticated |
| GET `/api/v1/data-conflicts/status/{status}` | EDITOR, ADMIN |
| PATCH `/api/v1/data-conflicts/{id}/review`, `/{id}/resolve` | ADMIN |
| POST `/api/v1/verifications` | ADMIN |
| GET `/api/v1/verifications?entityType=VILLAGE&entityId={id}` | Authenticated |
| GET `/api/v1/audit-logs/entity?entityType={type}&entityId={id}` | ADMIN |
| GET `/api/v1/audit-logs/actor/{actorUserId}`, `/action/{action}` | ADMIN |

These permissions preserve the existing policy. Missing/invalid authentication yields 401; insufficient roles yield 403. Login failures use the same `{"error":"Invalid credentials"}` for unknown users, wrong passwords and disabled users. Invalid login input gives 400. Existing data-quality errors use `{"error":"..."}`: 400 invalid input, 404 missing record, 409 invalid state or duplicate link. No source DELETE or audit mutation API exists.

## Sources

POST creates (201); PUT replaces mutable fields (200). Both use the same validated request shape (`CreateSourceRequest`), since field requirements are identical. PUT requires title/type; omitted optional fields become null. IDs and createdAt are preserved. Example body:

```json
{
  "title":"Village reference",
  "publisher":"Publisher",
  "author":"Author",
  "sourceType":"BOOK",
  "url":"https://example.com/reference",
  "publicationDate":"2020-01-01",
  "accessedAt":"2026-10-22",
  "citation":"Bibliographic citation"
}
```

Title is nonblank and at most 350 characters; publisher/author at most 200. sourceType is required: CENSUS, GOVERNMENT, BOOK, ARTICLE, ARCHIVE, WEBSITE, INTERVIEW, OTHER. URL is optional/null; when supplied it must be an absolute HTTP(S) URL with a valid host. Dates use ISO LocalDate. No new chronological restrictions were invented. Missing source IDs return 404; invalid IDs/fields return 400.

Response fields: id, title, publisher, author, sourceType, url, publicationDate, accessedAt, citation, createdAt. GET collection returns an array. Title search and source-type filtering remain available in the existing service/repository; this delivery adds no search query contract.

Source update audit snapshots contain title, publisher, author, sourceType, date strings, hasUrl and hasCitation. Update metadata additionally reports urlChanged/citationChanged. Raw URLs (which may contain embedded credentials/tokens) and unrestricted citation text are intentionally omitted; this is metadata history, not a complete document-version archive.

## Conflict and verification workflow

Create a conflict for an existing village:

```json
{
  "entityType":"VILLAGE",
  "entityId":123,
  "fieldName":"population",
  "conflictingValues":[{"source":"A","value":3500},{"source":"B","value":4200}]
}
```

The ID must exist; at least two nonempty value maps are required. entityType and fieldName are nonblank with maximum lengths 80 and 100. Other entity types are rejected. POST returns 201 and OPEN. Administrator PATCH `/{id}/review` has no body and moves OPEN to IN_REVIEW. Administrator PATCH `/{id}/resolve` accepts `{"resolutionNotes":"Reviewed evidence"}` and moves IN_REVIEW to RESOLVED. Notes must be nonblank; reviewer must exist and be enabled. No client reviewer-ID field is trusted. Repeat/backward/skipped transitions return 409; missing conflict returns 404. Row locks protect transition checks and audit snapshots within the same transaction.

Verification POST accepts `{"entityType":"VILLAGE","entityId":123,"decision":"VERIFIED","reason":"Checked references"}`. Supported decisions are VERIFIED, REJECTED and NEEDS_REVISION. They emit VERIFY, REJECT and UPDATE audit actions respectively, using the existing schema vocabulary. The last decision previously failed despite being supported by DTO/schema; this was a defect correction, not a workflow policy change.

Conflict resolution records the review outcome; it does not automatically overwrite the village field. Verification records likewise do not implement another member's village-status update workflow.

## Audit coverage and relationships

Audit rows capture authenticated database actor ID/name, action, entity type/ID, database timestamp and selected before/after metadata. Existing optional correlationId remains unset by these call sites. Required audit writes join the business transaction; if auditing fails, the business mutation rolls back. Administrator reads are enforced at controller and service levels. There are no exposed audit update/delete endpoints. Database-level append-only privileges/triggers are an operations concern; the application repository remains trusted internal code.

Actual covered mutations are source create/update, village-source link creation, conflict create/review/resolve, and verification creation. Conflict snapshots omit conflicting values and resolution notes; verification snapshots omit free-text reasons; link snapshots omit claim notes. No password hashes, JWTs or application secrets are deliberately included. Login and currently absent user-management/domain mutations are not claimed as audited business operations.

The existing village_sources composite key prevents duplicate links and its FKs protect both references. Linking validates IDs, village/source existence and duplicates before storage. Six existing tables reference sources with real FKs: village_sources, population_records, history_sources, event_sources, heritage_site_sources and cultural_heritage_sources. Population source is mandatory. Population/heritage APIs and their audit integrations depend on Member 2; the village public lookup contract depends on Member 1. Agriculture/community/integration services depend on Member 4. Existing VillageExistsRepository SQL lookup is retained until a public village API is implemented. No extra relationship or migration was invented.

## Login throttling

Every POST to the login path is counted before MVC body parsing/validation, including malformed JSON and failed or successful login. Each socket client IP may make five requests per fixed one-minute window starting with its first accepted request. The sixth request returns 429 with an empty body and `Retry-After: 60` (a conservative retry duration). Window expiration is checked using monotonic time; tests advance an injected clock without sleeping.

Admission, counters, capacity checks and cleanup are synchronized. Counters saturate instead of overflowing. Storage is capped at 10,000 clients; at capacity new clients receive 429 instead of evicting live counters. Expired entries are reclaimed on incoming traffic at most once per minute, and each tracked client's expired window resets immediately when that client requests again. Entries can remain while idle but storage stays bounded.

X-Forwarded-For and Forwarded headers are not used; forwarded-header processing is disabled by default. Behind a proxy the socket peer is the proxy, so clients may share its quota. Introduce a deliberate trusted-proxy configuration before changing this. Counters are per application instance, reset on restart, and are not distributed. Shared NATs share a quota; fixed windows permit boundary bursts. No Redis, infrastructure dependency or scheduler was introduced.

## Test evidence and remaining verification

- Original focused baseline: 14 tests, 0 failures, 0 errors, 0 skipped.
- Original full baseline: 106 tests, 0 failures, 77 setup errors, 0 skipped; local configured PostgreSQL credentials were rejected.
- First focused implementation run: 62 tests, 2 failures, 0 errors, 0 skipped; exposed missing generated timestamp retrieval, subsequently fixed.
- Intermediate clean full run: 132 tests, 0 failures, 0 errors, 0 skipped.
- Final `.\mvnw.cmd clean test`: **134 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Java 21, existing Maven/Spring versions, PostgreSQL 18.6. Both V1/V2 migrations replayed successfully on a fresh isolated database; schema validation, Flyway checksums and source FKs verified. No migration file changed.

The first sandbox wrapper invocation could not use the Maven cache; execution in the normal user environment resolved that. Tests then used an isolated local loopback PostgreSQL instance and a randomly generated test-only JWT key. The existing database and credentials were unchanged. The temporary server/data were removed after testing. Final console evidence is in ignored `target/member3-final.log`; JUnit XML/text reports are in `target/surefire-reports/`.

Manual steps remaining:

1. Configure valid development/test DB_PASSWORD and JWT_SECRET, and rerun clean tests against the team's PostgreSQL 17 environment. PostgreSQL 18 success does not establish version-17-specific compatibility.
2. Smoke-test actual HTTP login, source PUT, review/resolve and ADMIN audit reads with enabled accounts for each role.
3. Confirm deployment HTTPS, secret injection and any trusted proxy/network policy; check whether single-instance throttling matches deployment topology.
4. Agree cross-module public contracts and add audit/provenance integrations when other members' services exist.

The legacy `docs/database/tests/V1_data_integrity_tests.sql` is deliberately unchanged and was not executed. It is guarded for a dedicated PostgreSQL 17/V1-only empty database; its guards do not support a V2 role-seeded database. Do not run it on the normal application/test database. The new Maven migration test covers the current V1+V2 state.

## Exact changed-file inventory

Paths below are relative to the repository. No pom.xml, dependency, V1/V2 SQL, Git metadata or Postman file was changed.

| File | Reason |
|---|---|
| src/main/java/com/qaryati/dataquality/controller/SourceController.java | Add PUT using existing request/response shape |
| src/main/java/com/qaryati/dataquality/controller/DataConflictController.java | Reviewer identity resolved inside service |
| src/main/java/com/qaryati/dataquality/controller/VerificationRecordController.java | Reviewer identity resolved inside service |
| src/main/java/com/qaryati/dataquality/dto/CreateSourceRequest.java | Bean Validation for HTTP(S) URL |
| src/main/java/com/qaryati/dataquality/entity/AuditLog.java | Retrieve generated occurredAt |
| src/main/java/com/qaryati/dataquality/entity/DataConflict.java | Retrieve generated createdAt |
| src/main/java/com/qaryati/dataquality/entity/VerificationRecord.java | Retrieve generated reviewedAt |
| src/main/java/com/qaryati/dataquality/exception/DataQualityExceptionHandler.java | Consistent 400 for service validation failures |
| src/main/java/com/qaryati/dataquality/repository/DataConflictRepository.java | Lock transitions within transaction |
| src/main/java/com/qaryati/dataquality/repository/SourceRepository.java | Lock updates for coherent audit snapshots |
| src/main/java/com/qaryati/dataquality/service/SourceService.java | Validated audited replacement update; service authorization |
| src/main/java/com/qaryati/dataquality/service/VillageSourceService.java | Enforce existing role policy at service boundary |
| src/main/java/com/qaryati/dataquality/service/AuditLogService.java | Reject disabled audit actor |
| src/main/java/com/qaryati/dataquality/service/DataConflictService.java | Context identity, request validation and locked transitions |
| src/main/java/com/qaryati/dataquality/service/VerificationRecordService.java | Context identity, validation and NEEDS_REVISION audit support |
| src/main/java/com/qaryati/security/config/JwtConfig.java | Require subject and expiration alongside existing validators |
| src/main/java/com/qaryati/security/config/JwtAuthenticationConverterConfig.java | Generic bearer failures for missing/disabled account |
| src/main/java/com/qaryati/security/config/SecurityConfig.java | Install login throttle before bearer processing |
| src/main/java/com/qaryati/security/controller/LoginController.java | Generic 401/400 errors; move throttle before MVC validation |
| src/main/java/com/qaryati/security/ratelimit/LoginRateLimitService.java | Hard capacity, atomic admission/cleanup and saturating counters |
| src/main/java/com/qaryati/security/ratelimit/LoginRateLimitFilter.java (new) | Count all login requests before MVC validation |
| src/main/resources/application.properties | Explicitly disable untrusted forwarding-header processing |
| src/test/java/com/qaryati/DatabaseMigrationIntegrationTest.java (new) | Current migration checksums, schema and source FK contracts |
| src/test/java/com/qaryati/dataquality/controller/SourceControllerIntegrationTest.java | PUT, validation, roles, snapshots and transactional rollback |
| src/test/java/com/qaryati/dataquality/controller/DataConflictControllerIntegrationTest.java | Invalid/repeated transitions, missing/disabled targets, snapshot exclusions |
| src/test/java/com/qaryati/dataquality/controller/VerificationRecordControllerIntegrationTest.java | NEEDS_REVISION and generated timestamp regression |
| src/test/java/com/qaryati/dataquality/controller/AuditLogControllerIntegrationTest.java | No audit mutation endpoints even for ADMIN |
| src/test/java/com/qaryati/security/controller/LoginAuthenticationIntegrationTest.java (new) | Real login, generic failures, statelessness, missing/malformed JWT |
| src/test/java/com/qaryati/security/controller/LoginRateLimitIntegrationTest.java | Malformed body counting and forwarding-header spoof attempts |
| src/test/java/com/qaryati/security/ratelimit/LoginRateLimitServiceTest.java | Concurrent admission, hard capacity and cleanup/expiry |
| src/test/java/com/qaryati/security/service/JwtServiceTest.java | Missing/blank subject and missing expiration |
| docs/MEMBER_3.md (new) | API guide, audit, checklist, exact inventory, test evidence and limits |

Inspect tracked changes with:

```powershell
git -c safe.directory=C:/Users/HITECH/IdeaProjects/Qaryati diff --stat
git -c safe.directory=C:/Users/HITECH/IdeaProjects/Qaryati diff
```

New files remain untracked intentionally (no staging/committing requested). List them with `git -c safe.directory=C:/Users/HITECH/IdeaProjects/Qaryati ls-files --others --exclude-standard`; `git diff --no-index -- /dev/null <new-file>` displays a new file's patch and exits 1 when differences are present. Open the new files directly when reviewing on Windows if `/dev/null` is unavailable.
