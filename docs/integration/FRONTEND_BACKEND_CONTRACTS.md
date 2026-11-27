# Frontend-backend API contracts and integration handoff

Source snapshot: `Fawzia/functional-integration`, `29c1f0f`, 2026-11-27.
Frontend consumer: Roaa's `roaa/frontend-integration` branch. That branch and its
runtime configuration were not inspected. This document describes **existing
backend source contracts**, not a successful live integration or deployment.

No application, test, database, provider or k6 execution was performed for this
handoff. Historical offline results below were supplied by the developer.
No environment is approved for execution by this document.

## 1. Conventions and security boundary

- Paths below are complete application paths, without an assumed host or port.
  Send JSON write bodies with `Content-Type: application/json`.
- `R` = VIEWER, EDITOR or ADMIN; `W` = EDITOR or ADMIN; `A` = ADMIN;
  `AUTH` = any authenticated account, including one without a recognized role.
  **Every endpoint is authenticated unless its row explicitly says anonymous.**
- `Id` and `int64` mean JSON integers backed by signed 64-bit Java values; `Id`
  denotes a record reference. `int` means
  a 32-bit integer; `number` includes `BigDecimal`. Do not silently round IDs
  outside JavaScript's safe integer range. `date` is `YYYY-MM-DD`; `instant` and
  `offset-time` are ISO date-time strings. Enum spellings are case-sensitive.
- In schemas, `?` marks nullable/optional values. Send required request fields;
  do not assume missing nullable fields mean "leave unchanged" on PUT.
  Tolerate null/absent optional response properties unless explicitly pinned below.
- Listed domain errors are not an exhaustive list of infrastructure failures.
  Protected calls can return 401; role-restricted calls can also return 403. See section 10 for
  actual error-body differences; there is no universal JSON error envelope.
- There is no global pagination/filter convention. Village pages use `number`;
  Population/Heritage pages use `page`; other collections below are arrays.

[SecurityConfig](../../src/main/java/com/qaryati/security/config/SecurityConfig.java#L19)
enables method security, disables CSRF for stateless Bearer authentication and
creates no authentication session. Its only anonymous allowlist is POST login
and the two public Village GET patterns. Invalid Bearer headers are still
authenticated and rejected even on otherwise public routes. Do not attach an
expired token to public browsing requests.

## 2. Authentication

Sources: [controller](../../src/main/java/com/qaryati/security/controller/LoginController.java#L21),
[LoginRequest](../../src/main/java/com/qaryati/security/dto/LoginRequest.java),
[LoginResponse](../../src/main/java/com/qaryati/security/dto/LoginResponse.java),
[authority converter](../../src/main/java/com/qaryati/security/config/JwtAuthenticationConverterConfig.java#L25),
[login limiter](../../src/main/java/com/qaryati/security/ratelimit/LoginRateLimitFilter.java#L20).

| Method and path | Access / input | Success | Explicit failures |
| --- | --- | --- | --- |
| POST `/api/v1/auth/login` | Anonymous; `{username:string, password:string}`, both nonblank | 200 `{accessToken:string, tokenType:"Bearer", expiresIn:int64}` | 400 invalid input; 401 invalid credentials; 429 throttle |
| GET `/api/v1/auth/me` | AUTH; no body | 200 `{username:string, roles:string[]}` | 401 missing/invalid token or missing/disabled user |

`expiresIn` is seconds, computed from configured expiration minutes. The checked-in
default is 15 minutes; consume the response rather than assuming every deployment
uses 900 seconds. Keep tokens/credentials private and out of URLs, logs and examples.
There is no application refresh, registration or logout REST handler in this controller.

`/me` preserves the authenticated username. Roles come from current
`Authentication.getAuthorities()`: only `ROLE_VIEWER`, `ROLE_EDITOR`, `ROLE_ADMIN`
are retained, prefixes removed, duplicates removed, then names sorted. No recognized
authority produces `[]`; no default role is invented. Roles are resolved by the
existing database-backed JWT converter, not by browser-decoded JWT role claims.
Hiding buttons is not a substitute for backend authorization.

Synthetic `/me` response for an account with two effective roles:

```json
{"username":"synthetic-editor","roles":["ADMIN","EDITOR"]}
```

Login is limited to five POST attempts per direct socket IP per one-minute window
within an application process, including malformed requests. The limiter returns
429 with `Retry-After: 60` and no custom JSON body. Proxy deployment must account
for this socket-IP behavior; do not call login in measured performance iterations.

## 3. Public Village browsing and point-map data

Sources: [public controller](../../src/main/java/com/qaryati/village/controller/PublicVillageController.java#L18),
[public service methods](../../src/main/java/com/qaryati/village/service/VillageService.java#L89),
[restricted detail query](../../src/main/java/com/qaryati/village/repository/VillageRepository.java#L20),
[PublicVillageResponse](../../src/main/java/com/qaryati/village/dto/PublicVillageResponse.java#L8),
[PublicVillagePageResponse](../../src/main/java/com/qaryati/village/dto/PublicVillagePageResponse.java#L8).

| Method and path | Access / input | Success | Domain failures |
| --- | --- | --- | --- |
| GET `/api/v1/public/villages` | Anonymous; query table below | 200 public Village page | 400 invalid/unsupported query |
| GET `/api/v1/public/villages/{id}` | Anonymous; positive Id; **no query parameters** | 200 public Village | 400 invalid ID/query; 404 hidden or nonexistent ID |

Both routes select **VERIFIED AND non-archived** Villages. The list applies these
predicates in the repository query before pagination/counting. Missing, DRAFT,
UNDER_REVIEW, REJECTED and archived detail IDs share the same 404 contract.
Authenticated callers receive the same restricted public projection.

| Collection query | Default / validation |
| --- | --- |
| `page` | 0; nonnegative int; `page * size <= 2147483647` |
| `size` | 20; int 1-100 |
| `search` | Optional; surrounding whitespace stripped; blank means no search; at most 180 Java UTF-16 code units after stripping |
| `governorateId`, `localAuthorityId` | Optional positive Id filters; combine with AND; unknown positive IDs yield no matches, not a parent-existence error |
| `sortBy` | `id`; exact allowlist `id`, `nameAr`, `nameEn` |
| `sortDir` | `asc`; asc/desc case-insensitive; non-ID sorts add ID ascending as a tie-breaker |

**Any `status` parameter is rejected**, including empty, VERIFIED, duplicate or
nonpublic values. Other unsupported parameter names are also 400. Detail rejects
even collection query parameters. Search is a literal substring over Arabic name
or case-insensitive English name; `%`, `_`, `!` are escaped. Database collation
still governs name ordering/case behavior. URL-encode query values.

Public Village JSON has **exactly seven fields**, with nullable keys included:
`id:Id`, `nameAr:string`, `nameEn:string?`, `governorateId:Id`,
`localAuthorityId:Id?`, `latitude:number?`, `longitude:number?`.
It contains no status, timestamps, archive state, notes, sources or user IDs.

Village page envelope, for public and editorial variants:

```text
content: corresponding Village DTO[]
number: int, size: int, totalElements: int64, totalPages: int,
numberOfElements: int, first: boolean, last: boolean,
hasNext: boolean, hasPrevious: boolean, empty: boolean
```

Synthetic request and response; IDs/data below do not assert existing fixtures:

```http
GET /api/v1/public/villages?search=Synthetic&page=0&size=20&sortBy=id&sortDir=asc
```

```json
{
  "content":[{"id":101,"nameAr":"Synthetic Village","nameEn":null,
    "governorateId":11,"localAuthorityId":null,"latitude":31.9,"longitude":35.2}],
  "number":0,"size":20,"totalElements":1,"totalPages":1,
  "numberOfElements":1,"first":true,"last":true,
  "hasNext":false,"hasPrevious":false,"empty":false
}
```

Totals count only public-eligible Villages, including eligible Villages with no
coordinates. **Do not create markers for null coordinates.** Map clients may need
multiple pages; use the page metadata, not a single default page as the entire map.
Offset pagination is not a cross-request snapshot during concurrent changes.

## 4. Protected editorial Village and geographic catalogues

These original routes have **not** become anonymous. Sources:
[Village controller](../../src/main/java/com/qaryati/village/controller/VillageController.java#L13),
[service](../../src/main/java/com/qaryati/village/service/VillageService.java#L63),
[request](../../src/main/java/com/qaryati/village/dto/VillageRequest.java#L8),
[response](../../src/main/java/com/qaryati/village/dto/VillageResponse.java#L8).

| Method and path | Roles / body | Success | Domain failures |
| --- | --- | --- | --- |
| GET `/api/v1/villages` | R; editorial query below | 200 editorial Village page | 400 invalid query |
| GET `/api/v1/villages/{id}` | R; positive Id | 200 VillageResponse | 400 invalid ID; 404 missing |
| POST `/api/v1/villages` | W; VillageRequest | 201 VillageResponse, initially DRAFT | 400 body/parent mismatch; 404 parent; 409 duplicate/integrity |
| PUT `/api/v1/villages/{id}` | W; VillageRequest | 200 VillageResponse | 400 input/mismatch; 404 Village/parent; 409 lifecycle/reassignment/duplicate |
| DELETE `/api/v1/villages/{id}` | A | 204, **archive**, not physical deletion | 400 invalid ID; 404 missing |

Editorial collection parameters match public pagination/search/geographic filters,
but add `status` (exact DRAFT, UNDER_REVIEW, VERIFIED, REJECTED; absent/blank means
all statuses) and permit `createdAt`, `updatedAt` sorting in addition to the public
sort fields. The collection excludes archived rows. Detail can include archived
rows and any status for every R role. Never use these responses as anonymous data.

`VillageRequest`: required positive `governorateId:Id`, nonblank `nameAr:string`
(max 180), nonblank `locationDescription:string`; optional `localAuthorityId:Id>0`,
`nameEn:string` (max 180), `nameOrigin:string`, `latitude:number`, `longitude:number`,
`elevationM:number`, `areaKm2:number>0`. Coordinates are paired/null together,
bounded latitude +/-90 and longitude +/-180, with no rounding into NUMERIC(9,6).
Elevation must fit NUMERIC(9,2), area NUMERIC(12,3). Unknown fields, including
client-supplied status/IDs/timestamps, are rejected.

`VillageResponse`: `id:Id`, `governorateId:Id`, `localAuthorityId:Id?`,
`nameAr:string`, `nameEn:string?`, `nameOrigin:string?`, `locationDescription:string`,
`latitude:number?`, `longitude:number?`, `elevationM:number?`, `areaKm2:number?`,
`status:VillageStatus`, `createdAt:instant`, `updatedAt:instant`, `archivedAt:instant?`.

PUT replaces nullable values and requires an unarchived DRAFT Village. Governorate
reassignment is 409; Local Authority must belong to that Governorate (400).
Arabic name is unique within Governorate, including archived rows. Repeated archive
preserves the original archive timestamp. Creating a verification record does not
publish this DRAFT Village; see outstanding work.

Synthetic create body (protected; illustrative only):

```json
{"governorateId":11,"nameAr":"Synthetic Village","locationDescription":"Synthetic location",
 "nameEn":null,"localAuthorityId":null,"latitude":null,"longitude":null}
```

### Governorates and Local Authorities

Sources: [Governorate controller](../../src/main/java/com/qaryati/village/controller/GovernorateController.java#L25),
[request](../../src/main/java/com/qaryati/village/dto/GovernorateRequest.java),
[response](../../src/main/java/com/qaryati/village/dto/GovernorateResponse.java),
[service](../../src/main/java/com/qaryati/village/service/GovernorateService.java#L30);
[Local Authority controller](../../src/main/java/com/qaryati/village/controller/LocalAuthorityController.java#L23),
[request](../../src/main/java/com/qaryati/village/dto/LocalAuthorityRequest.java),
[response](../../src/main/java/com/qaryati/village/dto/LocalAuthorityResponse.java),
[service](../../src/main/java/com/qaryati/village/service/LocalAuthorityService.java#L36).

| Method and exact path(s) | Roles | Input -> success | Domain failures |
| --- | --- | --- | --- |
| GET `/api/v1/governorates` | R | None -> 200 GovernorateResponse[] | No resource lookup |
| GET `/api/v1/governorates/{id}` | R | Long path ID -> 200 GovernorateResponse | 400 binding; 404 missing |
| POST `/api/v1/governorates` | W | GovernorateRequest -> 201 GovernorateResponse | 400 validation; 409 duplicate/integrity |
| PUT `/api/v1/governorates/{id}` | W | GovernorateRequest -> 200 GovernorateResponse | 400 validation; 404 missing; 409 duplicate/integrity |
| DELETE `/api/v1/governorates/{id}` | **A only** | 204, physical deletion | 404 missing; 409 referenced |
| GET `/api/v1/local-authorities` | R | Optional `governorateId:Id` -> 200 LocalAuthorityResponse[] | 400 binding; 404 supplied Governorate missing |
| GET `/api/v1/local-authorities/{id}` | R | Long path ID -> 200 LocalAuthorityResponse | 400 binding; 404 missing |
| POST `/api/v1/local-authorities` | W | LocalAuthorityRequest -> 201 LocalAuthorityResponse | 400 validation; 404 Governorate; 409 duplicate/integrity |
| PUT `/api/v1/local-authorities/{id}` | W | LocalAuthorityRequest -> 200 LocalAuthorityResponse | 400 validation; 404 reference; 409 reassignment/duplicate/integrity |
| DELETE `/api/v1/local-authorities/{id}` | A | 204, physical deletion | 404 missing; 409 referenced |

This is the exact Governorate permission matrix: **GET R; POST/PUT W; DELETE A**.
Neither catalogue is public or has a publication-state field. Lists are unpaged
and unordered; an existing Governorate with no authorities yields `[]`. Unlike
Village paths, these legacy services do not explicitly reject every nonpositive
numeric path ID before lookup; do not promise a uniform 400 rule for them.

- `GovernorateRequest`: required nonblank `nameAr:string` max 120;
  `nameEn:string?` max 120; `code:string?` max 32. Response adds `id:Id` to these
  three fields. Arabic name and supplied code are unique.
- `LocalAuthorityRequest`: required positive `governorateId:Id`, nonblank
  `nameAr:string` max 180, `authorityType` in MUNICIPALITY, VILLAGE_COUNCIL, OTHER;
  `nameEn:string?` max 180. Response adds `id:Id` to these four fields.
  Arabic name is unique per Governorate; PUT cannot reassign Governorate.

### Protected alternative names and geographic relations

Sources: [names controller](../../src/main/java/com/qaryati/village/controller/VillageNameController.java#L12),
[request](../../src/main/java/com/qaryati/village/dto/VillageNameRequest.java),
[response](../../src/main/java/com/qaryati/village/dto/VillageNameResponse.java),
[service](../../src/main/java/com/qaryati/village/service/VillageNameService.java#L27);
[relations controller](../../src/main/java/com/qaryati/village/controller/VillageRelationController.java#L12),
[request](../../src/main/java/com/qaryati/village/dto/VillageRelationRequest.java),
[response](../../src/main/java/com/qaryati/village/dto/VillageRelationResponse.java),
[service](../../src/main/java/com/qaryati/village/service/VillageRelationService.java#L27).

| Method | Exact paths | Roles / input -> success |
| --- | --- | --- |
| GET | `/api/v1/villages/{villageId}/names`; `/api/v1/villages/{villageId}/relations` | R -> 200 corresponding DTO[], ID ascending |
| GET | `/api/v1/villages/{villageId}/names/{nameId}`; `/api/v1/villages/{villageId}/relations/{relationId}` | R -> 200 corresponding DTO |
| POST | `/api/v1/villages/{villageId}/names`; `/api/v1/villages/{villageId}/relations` | W; corresponding request -> 201 DTO |
| PUT | `/api/v1/villages/{villageId}/names/{nameId}`; `/api/v1/villages/{villageId}/relations/{relationId}` | W; corresponding request -> 200 DTO |
| DELETE | `/api/v1/villages/{villageId}/names/{nameId}`; `/api/v1/villages/{villageId}/relations/{relationId}` | A -> 204, physical child deletion |

All path IDs must be positive. Arrays have no pagination. Missing parent, child or
wrong-parent child returns 404; existing parent with no children returns `[]`.
Reads allow any parent status/archive state, and relations can identify hidden
targets. All mutations, including ADMIN deletion, require an unarchived DRAFT
source Village (otherwise 409). Duplicates are 409; invalid input is 400.

- `VillageNameRequest`: required nonblank `name:string` max 200,
  `languageCode:string` max 12, `nameType` in ALTERNATIVE, HISTORICAL, FORMER,
  TRANSLITERATION; `notes:string?`. Response: `id:Id`, `villageId:Id`,
  `name:string`, `languageCode:string`, `nameType:enum`, `notes:string?`.
- `VillageRelationRequest`: exactly one of positive JSON-integer
  `relatedVillageId:Id?` or nonblank `relatedPlaceName:string?` max 200;
  required `relationType` in NEARBY, ADJACENT, HISTORICALLY_RELATED, OTHER;
  `distanceKm:number? >=0`, at most 8 integer/2 fractional digits. Self-links are 400;
  targets may cross Governorates and have any lifecycle state. Response:
  `id:Id`, `villageId:Id`, `relatedVillageId:Id?`, `relatedPlaceName:string?`,
  `relationType:enum`, `distanceKm:number?`.

Both requests reject unknown fields. PUT replaces optional scalars; neither set
of handlers creates reciprocal relations or offers public nested routes.

## 5. Population and Heritage/historical sections

All operations below are protected. Each service enforces authentication/roles.
For **reads**, EDITOR/ADMIN see all child statuses; VIEWER sees VERIFIED children
only, filtered before counting. Hidden detail returns 404. For **all writes,
including DELETE**, EDITOR or ADMIN is permitted; these deletes are not ADMIN-only.

| Exact base `B` | POST/PUT schema | Response schema | Sources |
| --- | --- | --- | --- |
| `/api/v1/population-records` | PopulationRecordRequest | PopulationRecordResponse | [controller](../../src/main/java/com/qaryati/population/controller/PopulationRecordController.java#L11), [service](../../src/main/java/com/qaryati/population/service/PopulationRecordService.java#L38), [request](../../src/main/java/com/qaryati/population/dto/PopulationRecordRequest.java), [response](../../src/main/java/com/qaryati/population/dto/PopulationRecordResponse.java) |
| `/api/v1/historical-events` | HistoricalEventRequest | HistoricalEventResponse | [controller](../../src/main/java/com/qaryati/heritage/controller/HistoricalEventController.java#L11), [service](../../src/main/java/com/qaryati/heritage/service/HistoricalEventService.java#L35), [request](../../src/main/java/com/qaryati/heritage/dto/HistoricalEventRequest.java), [response](../../src/main/java/com/qaryati/heritage/dto/HistoricalEventResponse.java) |
| `/api/v1/heritage-sites` | HeritageSiteRequest | HeritageSiteResponse | [controller](../../src/main/java/com/qaryati/heritage/controller/HeritageSiteController.java#L11), [service](../../src/main/java/com/qaryati/heritage/service/HeritageSiteService.java#L34), [request](../../src/main/java/com/qaryati/heritage/dto/HeritageSiteRequest.java), [response](../../src/main/java/com/qaryati/heritage/dto/HeritageSiteResponse.java) |
| `/api/v1/village-historical-overviews` | VillageHistoricalOverviewRequest | VillageHistoricalOverviewResponse | [controller](../../src/main/java/com/qaryati/heritage/controller/VillageHistoricalOverviewController.java#L11), [service](../../src/main/java/com/qaryati/heritage/service/VillageHistoricalOverviewService.java#L35), [request](../../src/main/java/com/qaryati/heritage/dto/VillageHistoricalOverviewRequest.java), [response](../../src/main/java/com/qaryati/heritage/dto/VillageHistoricalOverviewResponse.java) |
| `/api/v1/cultural-heritage` | CulturalHeritageCrudRequest | CulturalHeritageCrudResponse | [controller](../../src/main/java/com/qaryati/heritage/controller/CulturalHeritageCrudController.java#L11), [service](../../src/main/java/com/qaryati/heritage/service/CulturalHeritageCrudService.java#L35), [request](../../src/main/java/com/qaryati/heritage/dto/CulturalHeritageCrudRequest.java), [response](../../src/main/java/com/qaryati/heritage/dto/CulturalHeritageCrudResponse.java) |

For **each exact base in this table**, these five existing endpoints apply
(substitute the literal base for `B`; there are 25 operations):

| Method / path | Role | Success | Domain failures |
| --- | --- | --- | --- |
| POST `B` | W; corresponding full request | 201 corresponding DTO; `Location: B/{newId}` | 400 validation; 404 reference; 409 parent/duplicate |
| GET `B` | R; list query below | 200 five-field page | 400 query |
| GET `B/{id}` | R; positive Id | 200 corresponding DTO | 400 ID; 404 missing/hidden |
| PUT `B/{id}` | W; corresponding full request | 200 corresponding DTO | 400 input; 404 child/reference; 409 lifecycle/reassignment/parent/duplicate |
| DELETE `B/{id}` | W; positive Id | 204, physical deletion | 400 ID; 404 missing; 409 non-DRAFT |

All lists accept `page=0`, `size=20`, with page>=0 and size 1-100.
Population sorts `referenceYear DESC, id ASC` and **does not accept a villageId
filter**. All four Heritage lists accept optional positive `villageId` and sort
ID ascending. Cultural Heritage also accepts `heritageType` and
`verificationStatus`; requested status cannot broaden VIEWER visibility. There
is no client sort parameter. Unknown query names are not explicitly rejected by
these controllers, so a Population `?villageId=...` must not be mistaken for a filter.
Positive unmatched Heritage filters yield an empty page without checking parent existence.

Each page declares `content:corresponding DTO[]`, `page:int`, `size:int`,
`totalElements:int64`, `totalPages:int` (note **page**, not Village's **number**).
See [PopulationRecordPage](../../src/main/java/com/qaryati/population/dto/PopulationRecordPage.java)
and the four corresponding Heritage `*Page` records.

### Request schemas and validation

| Request | Required fields | Optional fields / constraints |
| --- | --- | --- |
| PopulationRecordRequest | `villageId:Id>0`, `referenceYear:int`1800-2200, `populationCount:int>=0`, `methodology:enum`, `sourceId:Id>0` | `referenceDate:date?` must have same year; `notes:string?` |
| HistoricalEventRequest | `villageId:Id>0`, nonblank `title:string` max 250, nonblank `description:string`, `datePrecision:enum`, nonempty positive `sourceIds:Id[]` | `eventDate:date?`, `yearFrom:int?`, `yearTo:int?` (years 1-2200); combinations below |
| HeritageSiteRequest | `villageId:Id>0`, nonnull `name:string` max 250, `siteType:enum`, nonempty positive `sourceIds:Id[]` | `description:string?`, `conditionNotes:string?`, paired `latitude:number?` +/-90 and `longitude:number?` +/-180, at most 6 fractional digits |
| VillageHistoricalOverviewRequest | `villageId:Id>0`, nonblank `overview:string`, nonempty positive `sourceIds:Id[]` | None |
| CulturalHeritageCrudRequest | `villageId:Id>0`, nonnull `name:string` max 200, `heritageType:enum`, nonnull `description:string`, nonempty positive `sourceIds:Id[]` | None |

Site names and Cultural names/descriptions are nonnull constraints, **not nonblank**;
empty/whitespace values are not rejected by those constraints. All five requests
reject unknown fields, including IDs, timestamps and verificationStatus. PUT is
full replacement, cannot change villageId, and clears omitted nullable scalars.
Heritage sourceIds are replaced as a set; missing/null/empty arrays are rejected,
not treated as "keep existing" or "clear sources". Referenced sources must exist.

Historical event precision: EXACT requires eventDate only; YEAR requires yearFrom
only; PERIOD requires both years with yearTo>=yearFrom; UNKNOWN requires all date/year
fields null. [Date validator](../../src/main/java/com/qaryati/heritage/validation/EventDateValidator.java#L13).

Exact enum values: Methodology = CENSUS, ESTIMATE, REGISTER, OTHER;
DatePrecision = EXACT, YEAR, PERIOD, UNKNOWN;
HeritageSiteType = ARCHAEOLOGICAL, TRADITIONAL_BUILDING, SPRING, RELIGIOUS, VIEWPOINT, OTHER;
CulturalHeritageType = CRAFT, FOOD, TRADITION, PRACTICE, OTHER.
Population/Heritage VerificationStatus = DRAFT, UNDER_REVIEW, VERIFIED, REJECTED,
DISPUTED (not the four-value VillageStatus). See matching `entity` enum files.

### Response schemas and lifecycle

| Response | Exact fields/types |
| --- | --- |
| PopulationRecordResponse | `id:Id`, `villageId:Id`, `referenceYear:int`, `referenceDate:date?`, `populationCount:int`, `methodology:enum`, `sourceId:Id`, `notes:string?`, `verificationStatus:enum`, `createdAt:instant`, `updatedAt:instant` |
| HistoricalEventResponse | `id:Id`, `villageId:Id`, `title:string`, `description:string`, `eventDate:date?`, `yearFrom:int?`, `yearTo:int?`, `datePrecision:enum`, `verificationStatus:enum`, `createdAt:instant`, `sourceIds:Id[]` |
| HeritageSiteResponse | `id:Id`, `villageId:Id`, `name:string`, `siteType:enum`, `description:string?`, `latitude:number?`, `longitude:number?`, `conditionNotes:string?`, `verificationStatus:enum`, `sourceIds:Id[]` |
| VillageHistoricalOverviewResponse | `id:Id`, `villageId:Id`, `overview:string`, `updatedAt:instant`, `verificationStatus:enum`, `sourceIds:Id[]` |
| CulturalHeritageCrudResponse | `id:Id`, `villageId:Id`, `name:string`, `heritageType:enum`, `description:string`, `verificationStatus:enum`, `sourceIds:Id[]` |

Heritage sourceIds are sorted/distinct; legacy uncited reads can return `[]`.
POST always creates DRAFT. POST/PUT require a VERIFIED, non-archived parent Village;
PUT/DELETE require a DRAFT child. Reads and DRAFT deletion do not recheck parent
eligibility: a VERIFIED child can remain readable after its parent is archived.
These are **protected sections**, not automatically public Village subresources.

Population duplicates `(villageId,referenceYear,methodology,sourceId)`, duplicate
overview per village, and Cultural duplicates `(villageId,name,heritageType)` yield 409.
Writes call audit APIs; Heritage writes also maintain source links. Neither this
section's CRUD handlers nor Data Quality supplies a Population/Heritage publication
transition. Use the wired Overview/Crud DTO names above, not older foundation
`VillageHistoryRequest` or `CulturalHeritageRequest` types.

## 6. Agriculture and Community

Sources: [product controller](../../src/main/java/com/qaryati/agriculture/controller/AgriculturalProductController.java#L25),
[village-agriculture controller](../../src/main/java/com/qaryati/agriculture/controller/VillageAgricultureController.java#L24),
[facility controller](../../src/main/java/com/qaryati/community/controller/CommunityFacilityController.java#L32),
[economic controller](../../src/main/java/com/qaryati/community/controller/EconomicActivityController.java#L32).
All are protected; GET=R, POST/PUT=W, DELETE=A.

| Exact base `B` | POST/PUT body -> response | Existing GETs |
| --- | --- | --- |
| `/api/v1/agricultural-products` | AgriculturalProductRequest -> AgriculturalProductResponse | GET `B` -> array; GET `B/{id}` -> object |
| `/api/v1/village-agriculture` | VillageAgricultureRequest -> VillageAgricultureResponse | GET `B/{id}` -> object; GET `B/village/{villageId}` and `B/product/{productId}` -> arrays; **no GET B** |
| `/api/v1/community-facilities` | CommunityFacilityRequest -> CommunityFacilityResponse | GET `B` optionally `?villageId=Id`; GET `B/{id}` |
| `/api/v1/economic-activities` | EconomicActivityRequest -> EconomicActivityResponse | GET `B` optionally `?villageId=Id`; GET `B/{id}` |

For each literal base, POST `B` returns 201 corresponding DTO, PUT `B/{id}` returns 200
corresponding DTO, DELETE `B/{id}` returns 204 with no body; GET returns 200.
Lists are arrays without pagination, search or specified ordering. Community
village filters and Village Agriculture IDs must be positive; unmatched positive
parent filters may return `[]`. Product path IDs have no explicit positive-ID guard.

| POST/PUT request schema | Corresponding response schema |
| --- | --- |
| [AgriculturalProductRequest](../../src/main/java/com/qaryati/agriculture/dto/AgriculturalProductRequest.java): required nonblank `nameAr:string` max 160, `productType:enum`; `nameEn:string?` max 160 | [AgriculturalProductResponse](../../src/main/java/com/qaryati/agriculture/dto/AgriculturalProductResponse.java): `id:Id`, `nameAr:string`, `nameEn:string?`, `productType:string` |
| [VillageAgricultureRequest](../../src/main/java/com/qaryati/agriculture/dto/VillageAgricultureRequest.java): required positive `villageId:Id`, `productId:Id`; `description:string?`, `importance:enum?` | [VillageAgricultureResponse](../../src/main/java/com/qaryati/agriculture/dto/VillageAgricultureResponse.java): `id:Id`, `villageId:Id`, `productId:Id`, `description:string?`, `importance:enum?` |
| [CommunityFacilityRequest](../../src/main/java/com/qaryati/community/dto/CommunityFacilityRequest.java): required positive `villageId:Id`, nonblank `name:string` max 200, `facilityType:enum`; `description:string?`, paired `latitude:number?` +/-90, `longitude:number?` +/-180, max 6 fractional digits | [CommunityFacilityResponse](../../src/main/java/com/qaryati/community/dto/CommunityFacilityResponse.java): `id:Id`, `villageId:Id`, `name:string`, `facilityType:enum`, `description:string?`, `latitude:number?`, `longitude:number?` |
| [EconomicActivityRequest](../../src/main/java/com/qaryati/community/dto/EconomicActivityRequest.java): required positive `villageId:Id`, nonblank `name:string` max 200, `activityType:enum`; `description:string?` | [EconomicActivityResponse](../../src/main/java/com/qaryati/community/dto/EconomicActivityResponse.java): `id:Id`, `villageId:Id`, `name:string`, `activityType:enum`, `description:string?` |

Enums: AgriculturalProductType = CROP, LIVESTOCK, PROCESSED_PRODUCT, OTHER;
AgricultureImportance = LOW, MEDIUM, HIGH;
CommunityFacilityType = SCHOOL, CLINIC, LIBRARY, COMMUNITY_CENTER, PARK, SPORTS, OTHER;
EconomicActivityType = AGRICULTURE, CRAFT, INDUSTRY, TRADE, TOURISM, OTHER.
See the corresponding `agriculture/entity` and `community/entity` enum files.

PUT replaces optional fields. Village Agriculture cannot change its village/product
pair (400); Community PUT can change village if the new parent exists. Parent
checks are existence-only, without VERIFIED/archive eligibility. Reads expose no
publication filter; these are not anonymous map/detail datasets. Deletes physically
remove rows. Error contracts:400 invalid input,404 missing object/write reference,
409 integrity conflict. Dedicated duplicate rules exist for Arabic product name,
village/product pair and `(villageId,name,activityType)`; referenced product deletion
is 409. Facilities have no dedicated duplicate-name rule.
See [VillageAgricultureService](../../src/main/java/com/qaryati/agriculture/service/VillageAgricultureService.java#L39),
[EconomicActivityService](../../src/main/java/com/qaryati/community/service/EconomicActivityService.java#L38)
and [CommunityFacilityService](../../src/main/java/com/qaryati/community/service/CommunityFacilityService.java#L37).

## 7. Sources, linking, verification, conflicts and audit

These are protected internal/editorial APIs, **not public provenance projections**.
All collections are unpaged arrays. Sources/link ordering is unspecified;
verification lists order reviewedAt descending, conflicts createdAt descending,
and audits occurredAt descending.

| Method and exact path | Role / input | Success | Domain failures |
| --- | --- | --- | --- |
| POST `/api/v1/sources` | W; CreateSourceRequest | 201 SourceResponse | 400 input |
| GET `/api/v1/sources` | R; no documented query filters | 200 SourceResponse[] | Infrastructure failures possible |
| GET `/api/v1/sources/{id}` | R; positive Id | 200 SourceResponse | 400 ID; 404 missing |
| PUT `/api/v1/sources/{id}` | W; positive Id; CreateSourceRequest | 200 SourceResponse | 400 input; 404 missing |
| POST `/api/v1/villages/{villageId}/sources` | W; positive villageId; LinkSourceRequest | 201 VillageSourceResponse | 400 input; 404 parent/source; 409 duplicate |
| GET `/api/v1/villages/{villageId}/sources` | R; positive villageId | 200 VillageSourceResponse[] | 400 ID |
| GET `/api/v1/sources/{sourceId}/villages` | R; positive sourceId | 200 VillageSourceResponse[] | 400 ID |
| POST `/api/v1/verifications` | A; CreateVerificationRequest | 201 VerificationRecordResponse | 400 input; 404 reference; 409 reviewer state |
| GET `/api/v1/verifications` | AUTH; required `entityType=VILLAGE`, positive `entityId` | 200 VerificationRecordResponse[] | 400 query |
| POST `/api/v1/data-conflicts` | W; CreateDataConflictRequest | 201 DataConflictResponse in OPEN state | 400 input; 404 Village |
| GET `/api/v1/data-conflicts` | AUTH; required exact `entityType=VILLAGE`, positive `entityId` | 200 DataConflictResponse[] | 400 query |
| GET `/api/v1/data-conflicts/status/{status}` | W; OPEN, IN_REVIEW or RESOLVED | 200 DataConflictResponse[] | 400 enum |
| PATCH `/api/v1/data-conflicts/{id}/review` | A; positive Id; no body | 200 DataConflictResponse | 400 ID; 404 missing; 409 transition |
| PATCH `/api/v1/data-conflicts/{id}/resolve` | A; positive Id; ResolveDataConflictRequest | 200 DataConflictResponse | 400 input; 404 reference; 409 transition |
| GET `/api/v1/audit-logs/entity` | A; required nonblank `entityType`, positive `entityId` | 200 AuditLogResponse[] | 400 query |
| GET `/api/v1/audit-logs/actor/{actorUserId}` | A; positive actor Id | 200 AuditLogResponse[] | 400 ID |
| GET `/api/v1/audit-logs/action/{action}` | A; exact allowed action below | 200 AuditLogResponse[] | 400 action |

Route/permission sources: [SourceController](../../src/main/java/com/qaryati/dataquality/controller/SourceController.java#L27),
[VillageSourceController](../../src/main/java/com/qaryati/dataquality/controller/VillageSourceController.java#L29),
[VerificationRecordController](../../src/main/java/com/qaryati/dataquality/controller/VerificationRecordController.java#L28),
[DataConflictController](../../src/main/java/com/qaryati/dataquality/controller/DataConflictController.java#L31),
[AuditLogController](../../src/main/java/com/qaryati/dataquality/controller/AuditLogController.java#L13).
Service authorization also applies; controller annotations alone are not the full policy.

### Request and response schemas

| Request DTO | Fields |
| --- | --- |
| [CreateSourceRequest](../../src/main/java/com/qaryati/dataquality/dto/CreateSourceRequest.java#L12) | Required nonblank `title:string` max 350, `sourceType:enum`; `publisher:string?`, `author:string?` max 200; `url:string?` valid absolute hosted HTTP(S) without whitespace; `publicationDate:date?`, `accessedAt:date?`, `citation:string?` |
| [LinkSourceRequest](../../src/main/java/com/qaryati/dataquality/dto/LinkSourceRequest.java#L7) | Required positive `sourceId:Id`; `claimNote:string?` |
| [CreateVerificationRequest](../../src/main/java/com/qaryati/dataquality/dto/CreateVerificationRequest.java#L10) | Required nonblank `entityType:string` max 80, positive `entityId:Id`, `decision:enum`; `reason:string?` |
| [CreateDataConflictRequest](../../src/main/java/com/qaryati/dataquality/dto/CreateDataConflictRequest.java#L14) | Required nonblank `entityType:string` max 80, positive `entityId:Id`, nonblank `fieldName:string` max 100, `conflictingValues:object[]` with at least 2 nonempty maps, nonblank keys and nonnull values |
| [ResolveDataConflictRequest](../../src/main/java/com/qaryati/dataquality/dto/ResolveDataConflictRequest.java#L6) | Required nonblank `resolutionNotes:string` |

SourceType = CENSUS, GOVERNMENT, BOOK, ARTICLE, ARCHIVE, WEBSITE, INTERVIEW, OTHER.
VerificationDecision = VERIFIED, REJECTED, NEEDS_REVISION. ConflictStatus = OPEN,
IN_REVIEW, RESOLVED. Allowed audit actions = CREATE, UPDATE, DELETE, VERIFY, REJECT,
RESTORE ([AuditLogService](../../src/main/java/com/qaryati/dataquality/service/AuditLogService.java#L23)).

| Response DTO | Exact fields/types |
| --- | --- |
| [SourceResponse](../../src/main/java/com/qaryati/dataquality/dto/SourceResponse.java#L9) | `id:Id`, `title:string`, `publisher:string?`, `author:string?`, `sourceType:string`, `url:string?`, `publicationDate:date?`, `accessedAt:date?`, `citation:string?`, `createdAt:offset-time` |
| [VillageSourceResponse](../../src/main/java/com/qaryati/dataquality/dto/VillageSourceResponse.java#L6) | `villageId:Id`, `sourceId:Id`, `claimNote:string?` |
| [VerificationRecordResponse](../../src/main/java/com/qaryati/dataquality/dto/VerificationRecordResponse.java#L9) | `id:Id`, `entityType:string`, `entityId:Id`, `reviewerUserId:Id`, `decision:enum`, `reason:string?`, `reviewedAt:offset-time` |
| [DataConflictResponse](../../src/main/java/com/qaryati/dataquality/dto/DataConflictResponse.java#L11) | `id:Id`, `entityType:string`, `entityId:Id`, `fieldName:string`, `conflictingValues:object[]`, `status:enum`, `resolutionNotes:string?`, `resolvedBy:Id?`, `createdAt:offset-time`, `resolvedAt:offset-time?` |
| [AuditLogResponse](../../src/main/java/com/qaryati/dataquality/dto/AuditLogResponse.java#L9) | `id:Id`, `actorUserId:Id?`, `actorLabel:string`, `entityType:string`, `entityId:Id`, `action:string`, `occurredAt:offset-time`, `beforeData:object?`, `afterData:object?`, `correlationId:string?`, `reason:string?` |

Source PUT replaces mutable fields, clears omitted optional fields and preserves
ID/createdAt. No source DELETE or HTTP title/type search exists; title/type search
methods are service-only. There is no link update/delete or audit mutation API.
Source URLs are stored, not fetched; URL validation does not strip credential-bearing
userinfo/query strings. Raw URLs, citations, claim notes and conflicting values are
not public-safe projections. Render free text as untrusted text, not raw HTML, and
avoid logging sensitive response bodies.

Verification/conflict operations support **VILLAGE only**. Use the exact spelling
for all requests; verification GET trims it, conflict GET does not. Creation checks
Village existence, not public eligibility. Link GETs and verification/conflict entity
lists do not check parent existence/public visibility; unknown positive IDs may
return `[]`. Reverse source links can identify otherwise hidden Villages.

Verification derives reviewer identity from authentication. **It records a decision
and audit, without changing Village.status or publishing the Village.** Conflict
workflow is OPEN -> IN_REVIEW -> RESOLVED; repeated/skipped/backward transitions
are 409. Resolve derives reviewer identity, stores trimmed notes and status, but
**does not apply a canonical field value to the Village**. fieldName is free text,
not a validated Village-column selector. Sources:
[verification service](../../src/main/java/com/qaryati/dataquality/service/VerificationRecordService.java#L45),
[conflict service](../../src/main/java/com/qaryati/dataquality/service/DataConflictService.java#L47),
[source service](../../src/main/java/com/qaryati/dataquality/service/SourceService.java#L80),
[link service](../../src/main/java/com/qaryati/dataquality/service/VillageSourceService.java#L40).

Synthetic verification body, showing schema only, not a publication command:

```json
{"entityType":"VILLAGE","entityId":101,"decision":"VERIFIED","reason":"Synthetic reference review"}
```

## 8. Geocoding and map boundaries

Sources: [controller](../../src/main/java/com/qaryati/integration/controller/GeocodingController.java#L23),
[service](../../src/main/java/com/qaryati/integration/service/GeocodingService.java#L46),
[response](../../src/main/java/com/qaryati/integration/dto/GeocodingResponse.java),
[result](../../src/main/java/com/qaryati/integration/dto/GeocodingResult.java),
[attribution](../../src/main/java/com/qaryati/integration/dto/GeocodingProviderAttribution.java).

| Method and path | Role / required query | Success |
| --- | --- | --- |
| GET `/api/v1/geocoding/search` | R; `query:string`, trimmed/nonblank, max 500 | 200 GeocodingResponse |
| GET `/api/v1/geocoding/reverse` | R; `lat:number` +/-90, `lon:number` +/-180 | 200 GeocodingResponse |

Response: `results:GeocodingResult[]`, `cached:boolean`,
`providerAttribution:{provider:string,text:string,url:string}`.
Each result has `latitude:number`, `longitude:number`, and nullable strings
`formattedAddress`, `addressLine1`, `addressLine2`, `city`, `country`, `sourceName`,
`sourceAttribution`. Preserve attribution in the UI. Empty results are valid 200,
not 404. No six-decimal input restriction exists here; destination Village/facility
forms must still satisfy their own coordinate precision constraints.

These GETs can consume an external provider and write/refresh cache entries. They
do not modify Village coordinates automatically. Unexpired valid cache returns
cached=true; missing/expired/invalid cache triggers provider retrieval. There is
no refresh/invalidation HTTP contract or stale-cache fallback after provider failure.
No geocoding-specific request limiter is implemented.

Source defaults are a 24-hour cache TTL, 2-second connect timeout and 5-second request
timeout. The adapter allows at most one retry for qualifying 5xx/IO failures, not
for 429, other 4xx or timeouts. Effective runtime configuration was not inspected.
Sources: [properties defaults](../../src/main/java/com/qaryati/integration/config/GeoapifyProperties.java#L12),
[retry handling](../../src/main/java/com/qaryati/integration/adapter/GeoapifyGeocodingProvider.java#L68).

Errors:400 invalid input;503 provider unconfigured when retrieval is required;
429 upstream rate limit;504 timeout;502 network/upstream/malformed provider data;
500 serialization or unhandled infrastructure failure. Do not treat a provider
failure as a missing Village. This API is not anonymous and is not a tile server,
rendered map, GeoJSON, polygon, bounding-box or clustering API.

## 9. Client pagination, nullable values and update handling

- Keep separate adapters for the eleven-field Village page, the five-field
  Population/Heritage page and unpaged arrays. Do not assume `content` on arrays.
- Follow `hasNext` for Village pages or page/totalPages for other pages. Retain
  active filters while advancing; do not infer page completion from null coordinates.
- Treat nullable coordinates as missing markers, not zero coordinates. Preserve
  numeric precision and supported scales when submitting an edited location.
- Full PUT forms must send all required fields and consciously retain or clear
  optional values; Heritage PUT must resend the full desired sourceIds set.
- Display editorial verificationStatus separately from Village.status; their
  enums and HTTP workflows are different. Avoid client-side "publish" assumptions.

## 10. Error responses and authentication expiry

| HTTP | Client behavior |
| --- | --- |
| 400 | Show input/filter errors; retain the form. Fix unsupported fields/enums/date combinations; do not retry unchanged. |
| 401 | Protected request has missing/expired/invalid authentication or disabled/missing user. Discard stale auth state and request login; do not loop retries. Login 401 uses generic invalid-credentials text. |
| 403 | Keep authentication; show insufficient permission and disable the unavailable action. Do not infer that a hidden public record exists. |
| 404 | Show unavailable/not found. Public missing and hidden IDs are deliberately indistinguishable; do not probe protected routes anonymously as fallback. |
| 409 | Explain duplicate/lifecycle/reference conflict; reload relevant state before offering retry. Never silently delete dependencies or alter publication state. |
| 429 | Stop repeated calls and respect Retry-After when present. Distinguish login throttling from provider throttling. |
| 5xx / non-JSON | Show a generic service failure; safely handle absent/unparseable bodies and avoid exposing internal details. |

Actual body families:

| Origin | Source-confirmed body |
| --- | --- |
| Login validation/authentication handlers | 400 `{"error":"Invalid request data"}`; 401 `{"error":"Invalid credentials"}` |
| Login throttle filter | 429 with `Retry-After: 60`, no custom body |
| Common handled ResourceNotFound/DuplicateResource/validation/ResponseStatusException | `{timestamp:instant,status:int,error:string,message:string,path:string}` |
| Population/Heritage scoped advice | Same five fields; controller exceptions map to 400/401/403/404/409 as appropriate; unexpected failures use 500 and `"Unexpected error"` |
| Data Quality scoped advice | One-field `{error:string}`:400 validation/malformed/binding/illegal argument,404 missing,409 illegal state/duplicate link |
| Security filters, unmatched framework failures, missing parameters outside scoped handlers | No shared custom JSON contract; use status/content type and optional body defensively |

Sources: [common advice](../../src/main/java/com/qaryati/common/exception/GlobalExceptionHandler.java#L18),
[error DTO](../../src/main/java/com/qaryati/common/response/ApiErrorResponse.java#L7),
[Population advice](../../src/main/java/com/qaryati/population/controller/PopulationRecordExceptionHandler.java#L15),
[Heritage example](../../src/main/java/com/qaryati/heritage/controller/HeritageSiteExceptionHandler.java#L15),
[Data Quality advice](../../src/main/java/com/qaryati/dataquality/exception/DataQualityExceptionHandler.java#L17).
Data Quality malformed JSON uses `"Invalid request body or source type"`; binding
uses `"Invalid parameter: <name>"`; missing Source text is normalized to
`"Source not found"`. Other module messages are not a machine-readable error-code enum.
Never assume a security-filter 401 has the same JSON as a controller-generated 401.

Synthetic public missing/hidden response shape:

```json
{"timestamp":"2026-11-27T00:00:00Z","status":404,"error":"Not Found",
 "message":"Village with id 101 not found","path":"/api/v1/public/villages/101"}
```

## 11. Current limitations and outstanding integration work

| Current state | Required integration decision/work; not implemented by this document |
| --- | --- |
| No CORS configuration found in backend controllers/configuration | Agree same-origin delivery or a frontend development proxy forwarding `/api/v1` to an independently verified backend. A separate-origin frontend requires explicit backend/edge CORS design and validation, including Authorization preflight; anonymous GET permission alone is not CORS. No ports/targets are approved here. |
| Only two Village GETs are anonymous | Governorate/Local Authority catalogues, names, relations, Population, Heritage, Agriculture, Community, sources, workflow/audit and geocoding remain protected. Public catalogue release and public detail sections need explicit policies and separate implementations. |
| Public DTO has seven scalar fields only | Public detail cannot render histories, population, provenance or other sections using protected endpoints without login. Do not copy editorial responses into public caches/pages. |
| Population list has only page/size | A per-Village demographic panel needs a separately implemented backend filter. Sending villageId now does not scope the results; filtering one global page in the browser gives incomplete results/counts. |
| Publication workflow is incomplete | Village creation is DRAFT; verification records do not update Village.status. No application HTTP publication transition was found for Village or Population/Heritage. Define ownership/transitions before promising "approve and publish" UI behavior. Do not work around this by changing a database manually. |
| Conflict resolution stores metadata/status only | Applying a selected value to the canonical Village is a separate missing workflow; do not show it as already applied. |
| Parent/child eligibility differs | Population/Heritage read visibility checks child status but not current parent eligibility; Agriculture/Community/link reads have no public parent filter. Public-safe child queries/projections remain outstanding. |
| Paging/filter/response contracts differ across modules | Use module-specific clients. Sources lack HTTP search/type filters; many arrays are unbounded; unknown query names may be ignored outside public Village. No generic search endpoint is implied. |
| Point coordinates exist, richer map APIs do not | First public map can page through eligible Village coordinates. No GeoJSON/boundaries/bbox/clustering/tiles contract exists; confirm actual frontend requirements before proposing endpoints. Geocoding does not persist a selected Village location. |
| No checked-in OpenAPI/Postman contract or frontend application found in this checkout | This source snapshot plus DTOs/MVC tests is the handoff contract. Frontend branch wiring, proxy deployment, browser behavior and a live end-to-end flow remain unverified. |

## 12. FRONTEND INTEGRATION CHECKLIST

- [ ] Public Village pages/search/map use `/api/v1/public/villages` and its detail route.
- [ ] Authenticated editorial/admin pages use protected endpoints and their actual per-module role matrix.
- [ ] Send JWT only where needed; omit stale/invalid Authorization headers on anonymous reads.
- [ ] After authentication, load effective roles from `/api/v1/auth/me`; handle empty roles.
- [ ] Keep backend RBAC authoritative; UI hiding is a usability feature, not authorization.
- [ ] Handle 400/401/403/404/409, optional/non-JSON bodies, null fields and all pagination formats.
- [ ] Keep public counts/records separate from editorial data; do not infer hidden state from 404.
- [ ] Resend complete PUT forms/source associations where required; display lifecycle conflicts.
- [ ] Resolve proxy/CORS, Population village filtering, publication workflows and required public sections before live integration.
- [ ] Review this snapshot against the exact backend/frontend commits selected for integration.
- [ ] Obtain separate environment/execution approval before any live application, database, provider or load-test validation.

## 13. PR handoff, evidence and validation still required

The six focused commits after baseline `ee7d9d2`, confirmed from Git history:

| Commit | Purpose |
| --- | --- |
| `6f851d8` | F1: isolate geocoding cache writes from request-level transactions |
| `f95b2cf` | F2: validate Village/source lookup IDs before repository access |
| `d8c455f` | F3: enforce Governorate endpoint role permissions |
| `5237fa2` | P2: add guarded k6 Village read script and documentation |
| `5c4a333` | B1B: add VERIFIED/non-archived public Village list/detail projection |
| `29c1f0f` | B2: expose effective roles in authenticated current-user response |

Developer-reported offline evidence: F1 17 tests passed; F2 44 passed; F3 and B1B
targeted regressions passed; latest B2 plus neighboring regression selection
**326 tests passed**. These are historical supplied results, not a new combined
suite run by this documentation task. They do not establish live PostgreSQL,
browser/CORS, concurrency or provider behavior.

P2 [k6 script](../../performance/k6/villages-read.js) and
[execution prerequisites](../../performance/k6/README.md) are implemented but
**not executed**. The current script targets the protected Village read API with
a supplied Bearer token; it is not a public-browsing benchmark. Authenticated
write performance scenarios, measured latency/p95,
throughput/error reports, a measured bottleneck and a controlled before/after
optimization remain outstanding. No performance measurements are claimed.

Live PostgreSQL integration remains **unverified for this functional handoff**.
Never use `qaryati_db` or `qaryati_governorate_dev` for integration/load validation.
`qaryati_test_db` remains unverified and unapproved. Any future execution needs a
separately provisioned, independently verified, exclusively owned synthetic
environment and explicit approval. This document neither enables flags nor
authorizes startup, migrations or load testing.

Review requested before merge: backend/security maintainer for visibility/RBAC
and auth; Roaa/frontend integrator for client models/routes/proxy handling;
Population/Heritage and Data Quality owners for unresolved workflows and section
contracts. Confirm repository contribution rules and PR target with maintainers;
these reviewer roles are handoff recommendations, not an assertion of CODEOWNERS.
Review the provided offline evidence and code diffs, agree outstanding scope,
and plan separately approved integration validation. No staging, commit, push or
PR creation was performed for this document.

Further implementation detail: [Village management](../village/VILLAGE_MANAGEMENT.md),
[Local Authorities](../village/LOCAL_AUTHORITIES.md), [Population](../population/POPULATION_RECORDS.md),
[Data Quality integration](../../src/main/java/com/qaryati/dataquality/INTEGRATION.md),
[security/Data Quality handoff](../MEMBER_3.md). Older documents may show the
pre-B2 username-only `/me` example; the current controller and section2 above
take precedence. Existing foundation documentation must not be mistaken for
additional wired HTTP endpoints.
