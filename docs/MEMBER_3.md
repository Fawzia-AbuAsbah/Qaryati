# Member 3 delivery and API guide

Code review date: 2026-11-27. Branch: `roaa/frontend-final-integration`.
Reviewed HEAD: `c6eeef30c6e92000757f85427978e15bed30d6db`; merged backend
baseline: `4e2d13793d345d55079bfeec99d39ae1c9489fec`.
This guide describes checked-out code, not unpublished contracts. Phase 5.10
changes documentation only. No application, database, browser, integration suite
or performance workload was started for this update.

## 1. Implementation and delivery status

Security and Data Quality have implemented services and HTTP endpoints.
Population, History/Heritage, Village, Agriculture and Community also have business
services; they are no longer package skeletons. Five local frontend integration
commits add effective roles, protected source linking, basic public Villages,
Home/Map and Vite proxies. Implemented features are ready for controlled live
testing; full delivery and live compatibility remain unverified.

| Area | Current implementation | Remaining limitation |
| --- | --- | --- |
| Authentication | BCrypt login, stateless JWT, database-backed authorities | Real account/expiry/proxy verification outstanding |
| Sources | Create, collection/detail read, replacement update | No DELETE or HTTP search/type filter |
| Village Source Linking | Protected reads, reverse associations, create-only links | No update/unlink/delete; links do not prove public eligibility |
| Verification | Authenticated history; ADMIN records decisions | VILLAGE only; no publication/status transition |
| Data Conflicts | History, status lists, create/review/resolve | VILLAGE only; no canonical Village field application |
| Audit | Transactional writer, ADMIN queries, reviewed snapshots | Cross-module coverage incomplete; arrays unpaged |
| Frontend | Member 3 routes/panels and public/protected separation | Secure interactive and live backend verification outstanding |

References: [integration contract](integration/FRONTEND_BACKEND_CONTRACTS.md),
[security configuration](../src/main/java/com/qaryati/security/config/SecurityConfig.java),
[Data Quality controllers](../src/main/java/com/qaryati/dataquality/controller),
[Data Quality services](../src/main/java/com/qaryati/dataquality/service), and
[frontend guide](../frontend/README.md). Code takes precedence over historical
phase descriptions.

## 2. Authentication, BCrypt and JWT

`AuthenticationService` delegates username/password authentication to Spring
Security. `DatabaseUserDetailsService` loads the password hash, enabled flag and
roles. `PasswordEncoderConfig` supplies `BCryptPasswordEncoder`; plaintext
passwords are compared through the encoder. Login requires nonblank string
`username` and `password`. The DTO/controller defines no maximum credential length
and does not trim submitted credentials. Unknown users, incorrect passwords and
disabled accounts receive the same generic 401 response.

No registration, account-management, password-reset, refresh-token or server
logout HTTP endpoint is implemented.

| Configuration | Checked-out behavior |
| --- | --- |
| `app.jwt.secret` / `JWT_SECRET` | External standard Base64 value; decoded key must contain at least 32 bytes |
| `app.jwt.expiration-minutes` / `APP_JWT_EXPIRATION_MINUTES` | Default 15 minutes; login returns seconds in expiresIn |
| Signing/verification | HS256; issuer fixed to `https://qaryati.example` in encoder and validator |
| Claims | Issuer, subject, issued-at and expiration generated; JwtService encodes no role grants |
| Validation | Signature/HS256, issuer, default timestamp validation, required nonblank subject and expiration; framework clock-skew tolerance applies |
| HTTP authentication | Stateless Bearer JWT, no cookie-authenticated session; CSRF disabled for this model |
| Database secret | `DB_PASSWORD` supplied externally; never include its value or JWT key material in Git/logs |

Every Bearer authentication reloads the database user. Missing or disabled users
receive 401. Stored role codes map to `ROLE_<code>`; the converter uses these
current authorities, not JWT role claims. Role changes affect authorization on
subsequent backend requests using the same token. No role hierarchy is configured.

`GET /api/v1/auth/me` returns `username` and `roles`. It keeps only ROLE_VIEWER,
ROLE_EDITOR and ROLE_ADMIN, removes `ROLE_`, deduplicates and sorts the names.
Multiple roles are preserved; no recognized authorities produces `roles: []`.
There is no default role.

Sources: [login controller](../src/main/java/com/qaryati/security/controller/LoginController.java),
[login DTO](../src/main/java/com/qaryati/security/dto/LoginRequest.java),
[password encoder](../src/main/java/com/qaryati/security/config/PasswordEncoderConfig.java),
[JWT configuration](../src/main/java/com/qaryati/security/config/JwtConfig.java),
[JWT service](../src/main/java/com/qaryati/security/service/JwtService.java),
[authority converter](../src/main/java/com/qaryati/security/config/JwtAuthenticationConverterConfig.java),
and [application properties](../src/main/resources/application.properties).

### Login throttling and proxy behavior

`LoginRateLimitFilter` counts every POST to `/api/v1/auth/login` before MVC body
parsing, including successful, failed and malformed requests. Each socket IP gets
five requests per fixed one-minute window starting with its first accepted
request. A sixth gets 429, an empty body and `Retry-After: 60`. This is a conservative
wait duration, not an exact remaining-window countdown.

Admission/counters/cleanup are synchronized and use monotonic time and saturating
counters. Storage is capped at 10,000 clients; new clients are rejected at capacity
without evicting live counters. Expired entries are reclaimed on incoming traffic
at most once per minute; a tracked client's expired window resets on its next
request. There is no background scheduler or distributed store. Counters reset on
restart and are separate per application instance.

The key is `request.getRemoteAddr()`. `Forwarded` and `X-Forwarded-For` are not used;
`server.forward-headers-strategy=none` is the default. Vite/reverse-proxy users can
share the proxy socket IP and its five-attempt quota; NAT users can share quotas
too. Coordinate live-test logins accordingly. Production needs an agreed
trusted-proxy/rate-limit policy. Do not blindly trust client forwarding headers
or disable throttling to work around shared-IP behavior.

Sources: [filter](../src/main/java/com/qaryati/security/ratelimit/LoginRateLimitFilter.java)
and [limiter](../src/main/java/com/qaryati/security/ratelimit/LoginRateLimitService.java).

## 3. Endpoint inventory and exact permissions

All paths include `/api/v1`. `R` means any one of VIEWER, EDITOR or ADMIN; `W` means
EDITOR or ADMIN; `A` means ADMIN; `Auth` means any authenticated account, including
an empty-role account. ADMIN is explicitly allowed where listed; inheritance from
VIEWER/EDITOR is not assumed. Schema names refer to section 4.

| HTTP method and path | Access | Request body or query | Success |
| --- | --- | --- | --- |
| POST `/api/v1/auth/login` | Anonymous allowed; throttled | LoginRequest | 200 LoginResponse |
| GET `/api/v1/auth/me` | Auth | None | 200 ProfileResponse |
| POST `/api/v1/sources` | W | SourceRequest | 201 SourceResponse |
| GET `/api/v1/sources` | R | None; no HTTP title/type/page filter | 200 SourceResponse[] |
| GET `/api/v1/sources/{id}` | R | Positive path ID | 200 SourceResponse |
| PUT `/api/v1/sources/{id}` | W | Positive ID; SourceRequest | 200 SourceResponse |
| GET `/api/v1/villages/{villageId}/sources` | R | Positive path ID | 200 VillageSourceResponse[] |
| POST `/api/v1/villages/{villageId}/sources` | W | Positive village ID; LinkRequest | 201 VillageSourceResponse |
| GET `/api/v1/sources/{sourceId}/villages` | R | Positive path ID | 200 VillageSourceResponse[] |
| GET `/api/v1/verifications` | Auth | Required entityType, positive entityId | 200 VerificationResponse[] |
| POST `/api/v1/verifications` | A | VerificationRequest | 201 VerificationResponse |
| GET `/api/v1/data-conflicts` | Auth | Required entityType, positive entityId | 200 ConflictResponse[] |
| GET `/api/v1/data-conflicts/status/{status}` | W | OPEN, IN_REVIEW or RESOLVED | 200 ConflictResponse[] |
| POST `/api/v1/data-conflicts` | W | ConflictRequest | 201 ConflictResponse |
| PATCH `/api/v1/data-conflicts/{id}/review` | A | Positive ID; no body | 200 ConflictResponse |
| PATCH `/api/v1/data-conflicts/{id}/resolve` | A | Positive ID; ResolveRequest | 200 ConflictResponse |
| GET `/api/v1/audit-logs/entity` | A | Required nonblank entityType, positive entityId | 200 AuditResponse[] |
| GET `/api/v1/audit-logs/actor/{actorUserId}` | A | Positive actor ID | 200 AuditResponse[] |
| GET `/api/v1/audit-logs/action/{action}` | A | Exact supported action | 200 AuditResponse[] |

DataConflictController delegates method authorization to its service. Other
role-restricted Member 3 controllers also have corresponding service checks.
Audit reads require ADMIN at controller and service boundaries. SecurityConfig
permits anonymous login POST and the two public Village GET routes; other
requests require authentication before controller/service rules apply.

### Relevant statuses and error bodies

| Status | Meaning and client handling |
| --- | --- |
| 400 | Invalid login/DTO data, malformed JSON, invalid enum, nonpositive/badly bound ID, unsupported workflow entity type; retain form and do not retry unchanged |
| 401 | Login failure; missing/invalid/expired Bearer authentication; missing/disabled database user. Current-session protected 401 clears frontend authentication |
| 403 | Authenticated account lacks an explicitly required role; keep identity and disable unavailable actions |
| 404 | Source detail/update missing; link-create Village/Source missing; verification/conflict create Village missing; conflict transition target missing |
| 409 | Existing/concurrent village-source link or invalid conflict lifecycle transition |
| 429 | Login throttle; honor Retry-After and avoid repeated attempts |
| 5xx/network failure | Infrastructure/transport failure; no guaranteed Member 3 body shape and no automatic mutation retry |

Login validation uses `{"error":"Invalid request data"}`; authentication failure
uses `{"error":"Invalid credentials"}`. Scoped Data Quality advice uses
`{error:string}` for handled validation/binding/missing-resource/illegal-state
errors. Malformed JSON uses `Invalid request body or source type`; missing Source
text is normalized to `Source not found`. Security-filter and missing-parameter/
framework failures do not promise that same JSON shape. Clients use status and
handle empty, non-JSON and differently shaped bodies. No general source-duplicate
409 or source-uniqueness contract is defined.

Source: [DataQualityExceptionHandler](../src/main/java/com/qaryati/dataquality/exception/DataQualityExceptionHandler.java).

## 4. Request and response schemas

`Id` is a positive Java Long serialized as a JSON number; path/query IDs bind to
Long. Frontend additionally rejects unsafe integers above 9007199254740991 and
uses canonical decimal strings for route identities. That client safety bound
is not the backend Long maximum. `?` denotes nullable response fields or optional
nullable request fields. Dates are ISO LocalDate; timestamps are OffsetDateTime.
These collections return arrays, not page objects.

### Authentication

- LoginRequest: required nonblank `username:string` and `password:string`.
- LoginResponse: `accessToken:string`, `tokenType:"Bearer"`, `expiresIn:number`
  (seconds; default 900).
- ProfileResponse: `username:string`, `roles:string[]` with exact recognized names
  VIEWER, EDITOR, ADMIN; sorted distinct values or `[]`.

### Sources

SourceRequest is `CreateSourceRequest`, used for POST and PUT:

| Field | Validation |
| --- | --- |
| `title:string` | Required/nonblank, max 350 |
| `sourceType:string` | Required exact enum CENSUS, GOVERNMENT, BOOK, ARTICLE, ARCHIVE, WEBSITE, INTERVIEW, OTHER |
| `publisher:string?`, `author:string?` | Max 200 each |
| `url:string?` | Bean Validation URL/pattern plus service URI validation: hosted absolute HTTP(S), no whitespace |
| `publicationDate:date?`, `accessedAt:date?` | ISO dates; no additional chronology rule |
| `citation:string?` | No application-level length limit in this DTO |

SourceResponse: `id:Id`, `title:string`, `publisher:string?`, `author:string?`,
`sourceType:string`, `url:string?`, `publicationDate:date?`, `accessedAt:date?`,
`citation:string?`, `createdAt:timestamp`.

PUT replaces all mutable fields, requires title/type, clears omitted optional
fields and preserves ID/createdAt. A row lock keeps update/audit snapshots in one
transaction. Collection GET has no documented ordering. Title search/type filters
are service/repository methods only; query parameters do not supply an HTTP
search contract. There is no Source DELETE or PATCH endpoint. URLs are stored,
not fetched; validation does not remove userinfo or sensitive query parameters.
Source metadata remains protected and should not be logged.

Sources: [request](../src/main/java/com/qaryati/dataquality/dto/CreateSourceRequest.java),
[response](../src/main/java/com/qaryati/dataquality/dto/SourceResponse.java),
[service](../src/main/java/com/qaryati/dataquality/service/SourceService.java).

### Village Source Linking

LinkRequest: required positive `sourceId:Id`; optional `claimNote:string?`.
VillageSourceResponse: `villageId:Id`, `sourceId:Id`, `claimNote:string?`.
The backend has no claim-note length limit and preserves whitespace/text without
trimming. Frontend maps an empty form note to null and preserves nonempty notes,
including whitespace-only notes.

Create checks both records exist, without requiring public Village eligibility.
The composite primary key and atomic `INSERT ... ON CONFLICT DO NOTHING` protect
concurrent creation: an existing link returns 409 and never replaces its note.
No link update, unlink or delete HTTP operation exists. List/reverse reads do not
check parent existence, status or archive eligibility; unknown positive IDs can
return `[]`, and reverse links can identify hidden editorial villages. Responses
contain association IDs/notes, not public Village details or source titles.
Association array ordering is not promised.

Sources: [request](../src/main/java/com/qaryati/dataquality/dto/LinkSourceRequest.java),
[response](../src/main/java/com/qaryati/dataquality/dto/VillageSourceResponse.java),
[service](../src/main/java/com/qaryati/dataquality/service/VillageSourceService.java),
[atomic insert](../src/main/java/com/qaryati/dataquality/repository/VillageSourceRepository.java).

### Verification Records

VerificationRequest: required nonblank `entityType:string` max 80, positive
`entityId:Id`, required `decision:string`, optional `reason:string?` with no DTO
length limit. Only VILLAGE is supported; create and history reads trim entityType.
Create checks Village existence, not public eligibility. Reviewer identity comes
from the authenticated enabled database account, never a supplied reviewer ID.

VerificationResponse: `id:Id`, `entityType:string`, `entityId:Id`,
`reviewerUserId:Id`, `decision:string`, `reason:string?`, `reviewedAt:timestamp`.
History is ordered by reviewedAt descending; unknown positive Village IDs may
return `[]` without a parent-existence/public-visibility check.

| Decision | Audit action |
| --- | --- |
| VERIFIED | VERIFY |
| REJECTED | REJECT |
| NEEDS_REVISION | UPDATE |

A decision creates verification/audit rows. It does not update Village.status,
publish a Village or change child verification statuses. There is no verification
update/delete HTTP endpoint.

Sources: [request](../src/main/java/com/qaryati/dataquality/dto/CreateVerificationRequest.java),
[response](../src/main/java/com/qaryati/dataquality/dto/VerificationRecordResponse.java),
[service](../src/main/java/com/qaryati/dataquality/service/VerificationRecordService.java).

### Data Conflicts

ConflictRequest: required nonblank `entityType:string` max 80, positive `entityId:Id`,
nonblank `fieldName:string` max 100, and `conflictingValues:object[]` with at least
two nonempty maps, nonblank keys and nonnull values. Nested JSON values are allowed.
Create trims entityType/fieldName, supports VILLAGE only and checks Village
existence without public eligibility. fieldName is free text, not a validated
Village-column selector.

ConflictResponse: `id:Id`, `entityType:string`, `entityId:Id`, `fieldName:string`,
`conflictingValues:object[]`, `status:string`, `resolutionNotes:string?`,
`resolvedBy:Id?`, `createdAt:timestamp`, `resolvedAt:timestamp?`.
Entity/status lists are ordered by createdAt descending. Entity GET requires exact
untrimmed VILLAGE; unknown positive IDs may return `[]`.

Workflow: OPEN -> IN_REVIEW -> RESOLVED. Review takes no body. ResolveRequest has
nonblank `resolutionNotes:string` with no DTO length limit. The service stores
trimmed notes and derives the enabled reviewer from authentication. Skipped,
repeated/backward transitions return 409. Pessimistic row locks serialize state
checks and audit snapshots. Resolution stores reviewer/notes/time/status but does
not overwrite a Village field. Beyond these workflow PATCH operations, no general
conflict-field update/delete or automatic canonical data application endpoint exists.

Sources: [create request](../src/main/java/com/qaryati/dataquality/dto/CreateDataConflictRequest.java),
[resolve request](../src/main/java/com/qaryati/dataquality/dto/ResolveDataConflictRequest.java),
[response](../src/main/java/com/qaryati/dataquality/dto/DataConflictResponse.java),
[service](../src/main/java/com/qaryati/dataquality/service/DataConflictService.java).

### Audit Logs

AuditResponse: `id:Id`, `actorUserId:Id?`, `actorLabel:string`, `entityType:string`,
`entityId:Id`, `action:string`, `occurredAt:timestamp`, `beforeData:object?`,
`afterData:object?`, `correlationId:string?`, `reason:string?`.
Exact action filters: CREATE, UPDATE, DELETE, VERIFY, REJECT, RESTORE. Filter
vocabulary does not imply that every action has an application workflow.

Entity lookup takes nonblank exact entityType without trimming and positive
entityId. Its read service does not impose the writer's 80-character/type-format
validation. Actor lookup takes a positive ID. Results are arrays ordered by
occurredAt descending, including `[]` for no matches. No global listing,
pagination, date-range or combined-filter contract exists. No audit mutation
endpoint exists. Entity lookup is not a complete aggregate Village history.

Sources: [response](../src/main/java/com/qaryati/dataquality/dto/AuditLogResponse.java),
[controller](../src/main/java/com/qaryati/dataquality/controller/AuditLogController.java),
[service](../src/main/java/com/qaryati/dataquality/service/AuditLogService.java).

## 5. Synthetic contract examples

Examples illustrate schemas only. IDs/names/timestamps are invented; no accounts
or records are provisioned and no requests were executed. Password/token
placeholders are not credentials or usable JWTs. Protected calls require a
permitted synthetic session in a separately approved environment; never record
its real token in documentation, screenshots or logs.

Login request and 200 response:

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"synthetic-editor","password":"<synthetic-password-placeholder>"}
```

```json
{"accessToken":"<token-placeholder-not-a-JWT>","tokenType":"Bearer","expiresIn":900}
```

GET `/api/v1/auth/me` can return either profile:

```json
{"username":"synthetic-editor","roles":["EDITOR","VIEWER"]}
```

```json
{"username":"synthetic-empty","roles":[]}
```

POST `/api/v1/sources` body (also the replacement body for PUT `/api/v1/sources/201`):

```json
{"title":"Synthetic reference","publisher":"Synthetic publisher","author":null,"sourceType":"BOOK","url":"https://example.com/reference","publicationDate":"2020-01-01","accessedAt":null,"citation":"Synthetic citation"}
```

201 SourceResponse (200 after PUT/GET detail); collection GET wraps objects in an array:

```json
{"id":201,"title":"Synthetic reference","publisher":"Synthetic publisher","author":null,"sourceType":"BOOK","url":"https://example.com/reference","publicationDate":"2020-01-01","accessedAt":null,"citation":"Synthetic citation","createdAt":"2026-11-27T10:00:00Z"}
```

POST `/api/v1/villages/101/sources` body and 201 response:

```json
{"sourceId":201,"claimNote":"  Synthetic claim note  "}
```

```json
{"villageId":101,"sourceId":201,"claimNote":"  Synthetic claim note  "}
```

Village-source GET and reverse GET `/api/v1/sources/201/villages` return arrays of
this response. A repeated link returns 409, for example:

```json
{"error":"Source is already linked to this village"}
```

POST `/api/v1/verifications` body and 201 response:

```json
{"entityType":"VILLAGE","entityId":101,"decision":"NEEDS_REVISION","reason":"Synthetic evidence review"}
```

```json
{"id":301,"entityType":"VILLAGE","entityId":101,"reviewerUserId":12,"decision":"NEEDS_REVISION","reason":"Synthetic evidence review","reviewedAt":"2026-11-27T10:01:00Z"}
```

GET `/api/v1/verifications?entityType=VILLAGE&entityId=101` returns an array of
VerificationResponse objects. The decision does not change Village publication.

POST `/api/v1/data-conflicts` body and 201 response:

```json
{"entityType":"VILLAGE","entityId":101,"fieldName":"nameEn","conflictingValues":[{"sourceId":201,"value":"Synthetic A"},{"sourceId":202,"value":"Synthetic B"}]}
```

```json
{"id":401,"entityType":"VILLAGE","entityId":101,"fieldName":"nameEn","conflictingValues":[{"sourceId":201,"value":"Synthetic A"},{"sourceId":202,"value":"Synthetic B"}],"status":"OPEN","resolutionNotes":null,"resolvedBy":null,"createdAt":"2026-11-27T10:02:00Z","resolvedAt":null}
```

PATCH `/api/v1/data-conflicts/401/review` has no body and returns the same fields
with status IN_REVIEW. PATCH `/api/v1/data-conflicts/401/resolve` takes:

```json
{"resolutionNotes":"  Synthetic review outcome  "}
```

Its 200 response can be:

```json
{"id":401,"entityType":"VILLAGE","entityId":101,"fieldName":"nameEn","conflictingValues":[{"sourceId":201,"value":"Synthetic A"},{"sourceId":202,"value":"Synthetic B"}],"status":"RESOLVED","resolutionNotes":"Synthetic review outcome","resolvedBy":12,"createdAt":"2026-11-27T10:02:00Z","resolvedAt":"2026-11-27T10:03:00Z"}
```

GET `/api/v1/data-conflicts?entityType=VILLAGE&entityId=101` and
`/api/v1/data-conflicts/status/RESOLVED` return arrays of ConflictResponse.
These responses do not establish that a canonical Village value was applied.

GET `/api/v1/audit-logs/entity?entityType=VILLAGE_SOURCE&entityId=101` can return:

```json
[{"id":501,"actorUserId":11,"actorLabel":"synthetic-editor","entityType":"VILLAGE_SOURCE","entityId":101,"action":"CREATE","occurredAt":"2026-11-27T10:00:30Z","beforeData":null,"afterData":{"villageId":101,"sourceId":201},"correlationId":null,"reason":"Source linked to village"}]
```

Actor/action reads use that response schema, for example GET
`/api/v1/audit-logs/actor/11` and GET `/api/v1/audit-logs/action/CREATE`.
Only ADMIN can issue audit reads, even when an example actor is an editor.

## 6. AuditWriter, transactions and coverage

[AuditWriter](../src/main/java/com/qaryati/dataquality/api/AuditWriter.java) is a
Java module interface, not an HTTP endpoint. AuditWriterService and
AuditLogService.record use REQUIRED transaction propagation. Owning services place
mutations/audits in one transaction; audit failure prevents successful business
commit. Standalone writer calls create their own transaction. These implementation
guarantees still require isolated PostgreSQL validation; no new database pass is
claimed here.

AuditLogService resolves the SecurityContext username through UserLookup, rejects
missing/anonymous/disabled actors and writes database user ID and username.
Clients cannot choose the actor. The entity retrieves database-generated occurredAt;
current call sites leave correlationId null. Record writes validate nonblank
entityType max 80, positive entity ID and supported action. The generic AuditWriter
additionally requires uppercase `[A-Z][A-Z0-9_]{0,79}` and nonnull CREATE/UPDATE/
DELETE AuditAction. Its numeric AuditSnapshot contains villageId, sourceId,
referenceYear and populationCount, omitting null fields. Typed History/Heritage
methods validate CREATE after-only, UPDATE both and DELETE before-only snapshots.

| Owner/domain | Current calls | Audit entity type |
| --- | --- | --- |
| Member 3 Sources | CREATE, UPDATE | SOURCE |
| Member 3 village-source links | CREATE | VILLAGE_SOURCE; entityId is villageId |
| Member 3 verification | VERIFY, REJECT, UPDATE for NEEDS_REVISION | VERIFICATION_RECORD |
| Member 3 conflicts | CREATE, UPDATE on review/resolve | DATA_CONFLICT |
| Member 2 Population | CREATE, UPDATE, DELETE through AuditWriter | POPULATION_RECORD |
| Member 2 historical overview | CREATE, UPDATE, DELETE through typed writer | VILLAGE_HISTORY |
| Member 2 historical events | CREATE, UPDATE, DELETE through typed writer | HISTORICAL_EVENT |
| Member 2 heritage sites | CREATE, UPDATE, DELETE through typed writer | HERITAGE_SITE |
| Member 2 cultural heritage | CREATE, UPDATE, DELETE through typed writer | CULTURAL_HERITAGE |
| Member 1 Village/Governorate/Local Authority/names/relations | No audit calls found in mutation services | Coverage gap |
| Member 4 agricultural products/village agriculture | No audit calls found in mutation services | Coverage gap |
| Member 4 community facilities/economic activities | No audit calls found in mutation services | Coverage gap |

References: [Population service](../src/main/java/com/qaryati/population/service/PopulationRecordService.java),
[Heritage services](../src/main/java/com/qaryati/heritage/service),
[Village services](../src/main/java/com/qaryati/village/service),
[Agriculture services](../src/main/java/com/qaryati/agriculture/service),
[Community services](../src/main/java/com/qaryati/community/service).
Domain owners add missing calls and appropriate snapshots; Member 3 supplies the
writer contract and coordinates auditing. Timestamps alone are not actor-aware
audit records. Login and absent account-management operations are not claimed
as audited business mutations.

Source snapshots omit raw URLs/citations and include hasUrl/hasCitation; updates
add urlChanged/citationChanged. Link audits omit claimNote; verification audits
omit free-text reason; conflict audits omit competing values/resolution notes.
Population snapshots contain the numeric fields above. Typed History/Heritage
snapshots include domain text and source IDs; Member 3 minimization rules must
not be generalized to every audit record. Audit output remains protected and
text is untrusted. No audit update/delete API exists; database append-only
privileges/triggers are an operations concern, not a repository guarantee.

Six tables reference sources: village_sources, population_records, history_sources,
event_sources, heritage_site_sources, cultural_heritage_sources. Population has
a required source; History/Heritage association handling belongs to its services.
These protected relationships do not constitute public provenance contracts.

## 7. Frontend integration

### Authentication and permission freshness

AuthProvider/authSession keep the token in memory only, never local/session
storage. Login obtains a token then calls /auth/me; timers and focus/visibility
checks enforce conservative frontend expiry. Logout clears token/profile and
cancels work. There is no persistent login, token refresh or backend logout/
revocation call. A copied valid JWT can remain usable until expiry unless the
backend rejects its database account.

authService validates the entire roles array, copies/freezes it and freezes the
profile. Multiple roles and `[]` remain intact. Missing/malformed/unknown roles,
including a recognized role mixed with an unknown one, become `roles: null` and
`rolesVerified: false`. No default role or JWT/username/storage-derived grant exists.
Empty/invalid roles deny Sources/audit and role-dependent mutations/status lists;
authentication-only verification/village-conflict reads remain allowed.

Navbar explicitly refreshes permissions through /auth/me; there is no role polling.
During checks, role-dependent actions/requests are blocked. Success replaces the
immutable profile, invalidates old services and suppresses late responses.
Network/403 refresh failure retains identity/previous profile but marks
`rolesStale: true` and displays stale/error state. Role-dependent work stays blocked
until successful revalidation; a cancelled retry cannot clear an existing stale
mark. rolesVerified denotes valid data, not freshness. Current-session 401 ends
authentication; 403 does not log out. Before refresh, controls can reflect an old
snapshot, while backend per-request authorities reject revoked permissions.
Frontend gates serve usability; backend authorization remains authoritative.

Generation checks, AbortControllers, current-profile/service checks and state
redaction cover logout, expiry, session replacement, village changes and late
responses. Services check current permission before dispatch and after response;
denied checks initiate no protected operation. Cancellation cannot undo committed
server mutations; uncertain writes need reconciliation, not automatic retries.

### Pages and panels

- `/sources` has role-aware navigation/route protection and create, replacement
  update, detail and reverse-association UI. It no longer mounts an identity-less
  linking form.
- API Village Details mounts Sources, Verification and village-conflict panels
  after matching a loaded safe numeric public Village identity and permissions.
  The Sources panel obtains the authenticated service, selects existing sources
  and preserves optional notes for create-only linking.
- Confirmed mutations remain successful independently of a failed following GET
  refresh. Refresh retries issue GET only. Duplicate submissions are blocked;
  link 409 preserves the existing association/note.
- `/data-conflicts` provides EDITOR/ADMIN status lists; ADMIN review/resolve follows
  the current confirmed lifecycle. Verification creation is ADMIN only.
- `/audit-logs` is ADMIN-only with entity/actor/action reads. Snapshots and other
  untrusted text render as text, not injected HTML.
- Logout/expiry/profile replacement or denied permission clears/redacts protected
  data. Reverse links show editorial IDs without claiming public availability.
  Hidden villages have no general protected Village-details UI for these panels.

References: [auth session](../frontend/src/services/authSession.js),
[role validation](../frontend/src/services/authRoles.js),
[access policy](../frontend/src/services/authAccess.js),
[panel wiring](../frontend/src/components/ApiVillageDetails.jsx),
[link state](../frontend/src/services/villageSourcesState.js).

### Public boundaries, modes and Vite proxy

`VITE_VILLAGE_EXPLORER_MODE` defaults to `mock`. Explicit `api` uses public Village
endpoints without API-error fallback to mocks. Basic browsing/identity capabilities
are enabled; public catalogues/rich sections stay disabled. Mock Details mounts no
editorial panels or protected section requests. Sources/authenticated management
pages use real protected clients separately from browsing mode; mock browsing
does not mock authentication.

Public list/detail requests use the plain client without Bearer tokens; all API
client fetches use `credentials: 'omit'`. Authenticated transports attach tokens
for protected requests. Public DTOs have seven Village fields and eligibility is
VERIFIED/non-archived. API Details never requests protected geographic catalogues,
Population, Heritage, Agriculture or Community as anonymous fallbacks. Public
requests stay token-free during authenticated sessions. Source metadata, claim
notes, workflow records and audits are not public DTO fields.

Vite uses `loadEnv(mode, frontendDirectory, 'DEV_')` for server-only
`DEV_API_PROXY_TARGET`. Development/preview bind to 127.0.0.1 and proxy /api/v1
with `changeOrigin: true`, TLS verification, no rewrite/auth injection/automatic
retry. Default native target: `http://127.0.0.1:8080`. Compose maps
`http://127.0.0.1:8081` to app port 8080. Client base: `VITE_API_BASE_URL=/api/v1`.
Targets must be HTTP(S) origins without credentials/non-root paths/query/fragments;
malformed targets fail clearly. Proxy connection logs use a static redacted
message instead of request URLs/headers/bodies. Supplied Authorization, methods,
queries, JSON bodies, statuses and Retry-After pass through.

DEV_ is not exposed through VITE_'s client prefix. VITE_ values are public and
compiled at build time: API-mode preview needs an API-mode build. Target changes
require Vite restart. No real environment file/secret belongs in Git. Production
dist does not run Vite proxy rules: supply SPA deep-link fallback and same-origin
/api/v1 reverse proxy/TLS, or explicit backend/edge CORS for separate-origin APIs,
including Authorization preflight. This branch supplies no backend CORS policy.

References: [Vite config](../frontend/vite.config.js),
[proxy helper](../frontend/config/apiProxy.js),
[example settings](../frontend/.env.example),
[frontend deployment instructions](../frontend/README.md#api-base-configuration-and-routing).

## 8. Validation evidence and limits

Last recorded frontend baseline at the reviewed HEAD: **1,043 passing tests**,
successful build/lint/whitespace checks. This is previous-phase evidence, not a
rerun in this documentation phase. No live, PostgreSQL integration or performance
success is asserted for the current delivery. Old backend run totals/migration
replay claims are not used as current readiness evidence.

| Evidence | Existing sources or previous recorded results | Does not establish |
| --- | --- | --- |
| Database-free backend tests | JWT/BCrypt/authority/limiter units, mocked MVC, service/transaction and safety-guard regressions exist; current combined suite not run here | Real JDBC, schema, accounts, database locks or PostgreSQL compatibility |
| Simulated frontend HTTP | Baseline includes synthetic Fetch request/DTO/RBAC/session/cancellation/error/mock regressions | Actual backend security, SQL, data or CORS |
| JSX rendering | Actual React/Vite components with substituted hooks and DOM-dependent Leaflet bindings | Browser effects/events/focus, real Leaflet, visual or responsive behavior |
| Vite transport fixtures | Previous phase records 55 new proxy/config checks within 1,043 total; actual dev/preview, dynamic loopback HTTP fixtures, cleanup regression | Backend business rules, PostgreSQL, browser CORS or production routing |
| Historical simulated browser | Phase 5.6 records 12 headless Edge scenarios against synthetic HTTP with sandbox disabled | Secure browser/live backend verification, real tiles or comprehensive keyboard/mobile coverage |
| Secure browser | Later sandbox-enabled attempts did not complete; no browser started here | No completed secure interactive/responsive/keyboard validation is claimed |
| Database-backed tests | Guarded repository/authorization/migration/HTTP test sources exist; not executed here | No current PostgreSQL pass or migration replay claimed |
| Performance | Guarded k6 protected Village-read script/prerequisites exist, not executed | No latency/p95/throughput/bottleneck/before-after measurements; write workload outstanding |

References: [frontend evidence](../frontend/README.md),
[offline auth contract tests](../src/test/java/com/qaryati/security/controller/LoginControllerTest.java),
[audit transaction tests](../src/test/java/com/qaryati/dataquality/service/AuditWriterServiceTest.java),
[JSX tests](../frontend/src/services/homeMapComponents.test.js),
[proxy tests](../frontend/src/services/viteProxy.test.js),
[performance prerequisites](../performance/k6/README.md).
Do not repeat historical disabled-sandbox testing. Future browsers must retain
normal sandboxing and security protections.

## 9. Isolated live-test prerequisites

Packaged defaults point to `localhost:5432/qaryati_db`; test properties point to
`localhost:5432/qaryati_test_db` with Flyway enabled. Neither database is assumed
disposable or approved for connection. Native app port is normally 8080; Compose
publishes app 8081 and PostgreSQL 5433. Compose has persistent storage/startup
Flyway, so ordinary startup is not safe test provisioning. Backend requires Java
21; Compose uses PostgreSQL 17. No services/database connections were used here.

Before live testing, obtain separate approval for a new exclusively owned
PostgreSQL instance/storage, unique database and least-privilege accounts. Verify
identity, ownership and emptiness before migrations; inspect datasource/Hikari/
Flyway/profile overrides so none targets existing databases. Apply V1/V2 only to
the verified fresh database, with clean disabled and no baseline-on-migrate
workaround. Then disable startup migrations for the fixture application and use
schema validation. No destructive database command, populated database reuse or
migration against an existing database belongs in this plan.

V2 seeds roles only. Approved isolated fixture provisioning must supply synthetic
VIEWER/EDITOR/ADMIN, multi-role/empty-role and disabled accounts with BCrypt hashes,
sources/geographic parents and known VERIFIED/non-verified/archived villages.
Seed initial states as fixtures, not a missing production publication workaround.
Include nullable/zero coordinates, expected counts and enough public villages for
multiple Map pages. Use separate reserved loopback database/app/frontend ports and
an explicit DEV_API_PROXY_TARGET for the isolated app. Keep credentials/JWTs/hashes
out of Git/reports/logs; avoid SQL bind/request-body logging. No fixture account or
secret is created by this document.

Existing safeguards need review before execution:

- [DedicatedPostgresSafety](../src/test/java/com/qaryati/heritage/DedicatedPostgresSafety.java)
  pins `jdbc:postgresql://localhost:5432/qaryati_test_db`. It requires exact test
  profile/opt-in, validates conflicts before connection, verifies database/user
  identity and disables Flyway/SQL initialization for ordinary guarded tests.
- [MigrationTestDatabaseGuard](../src/test/java/com/qaryati/MigrationTestDatabaseGuard.java)
  has separate explicit approval/freshness checks and a controlled datasource
  before V1/V2 migrations. Its address is also pinned.
- Other guards, including RealAuditPostgresGuard, also pin the existing test
  address. Opt-in strings/matching names/localhost do not prove isolation.
  Suites cannot simply be redirected to a new database. Agree an independently
  reviewed isolation-compatible harness without weakening fail-closed checks;
  do not enable guards against the existing test database.
- Maven excludes IntegrationTest/AuthorizationTest and selected context tests by
  default. A normal test result does not mean those suites ran. Review exact test
  selection and safeguards before invoking them.
- The legacy V1-only SQL integrity script is not an approved V2/live runner.

Approved validation should begin with public reads, then auth/RBAC and only then
authorized writes/audit checks. Budget shared-IP logins, use sandboxed browsers,
and check logout/expiry/revocation/late responses and distinct tile/API failures.
Stop owned processes afterward; retain isolated data until explicit cleanup
approval without destructive cleanup commands. These are prerequisites, not
startup authorization.

## 10. Remaining coordination and documentation

| Owner | Work or limitation |
| --- | --- |
| Member 1 / Village | Add Village/geographic/name/relation audits; own publication/status transitions; provide public catalogues/expanded Village contracts |
| Member 2 / Population/Heritage | Population collection still has page/size only, no villageId filter; public Population/History/Heritage and parent/child eligibility contracts missing; isolated audit validation pending |
| Member 4 / Agriculture/Community | Add missing mutation audit calls; define public agriculture/facility/economic projections and eligibility |
| Member 3 with domain owners | Define public provenance fields/policy; source/link metadata and notes stay protected; distinguish conflict resolution from canonical changes |
| Security/deployment | Proxy-aware throttling, TLS/secrets, production routing and CORS when needed |
| Shared validation | Isolated fixture/harness provisioning, secure browser/live PostgreSQL verification, required performance measurements |

Sources, links, verification histories, conflict lists and audit queries are
unpaged editorial collections. Measure large-data behavior; frontend pagination/
invented filters cannot supply absent contracts. Source deletion, unlinking,
account management, publication and canonical conflict application stay unsupported.
Basic public browsing can enter controlled testing independently of rich sections;
full delivery needs required contracts or explicit scope reduction. Frontend test
counts do not establish performance or final deployment/PR readiness.

Other documents need separate authorized updates: `ARCHITECTURE.md` still describes
skeleton/planned features; `docs/integration/FRONTEND_BACKEND_CONTRACTS.md` retains
historical no-frontend/proxy-readiness statements; final deployment/API documents
need the chosen production topology. No checked-in OpenAPI/Postman contract was
found. `frontend/README.md` retains historical gates alongside superseding sections;
earlier closed gates are not current capability status. Phase 5.10 changes only
`docs/MEMBER_3.md`; other files remain unchanged.
