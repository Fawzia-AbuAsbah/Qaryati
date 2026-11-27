# Qaryati frontend

The React/Vite app defaults to the existing mock data through
`src/services/villageService.js`. Home, Map, Explorer and Details share a gated
API mode. Mock Map and Details retain their existing renderers; mock Home keeps
its original featured villages, hero and navigation.
Starting the frontend does not start the backend or connect to PostgreSQL.

## Commands

Run from `frontend/` after installing the locked dependencies with `npm ci`:

```sh
npm run dev
npm test
npm run build
npm run lint
```

Tests use Node's built-in test runner, assertions, pure DTO fixtures and mocked Fetch responses.
Use the existing Node.js 24 runtime; no test dependency or live API is needed.

## API base configuration and routing

`src/services/apiClient.js` reads `import.meta.env.VITE_API_BASE_URL`. The default
is `/api/v1`. Copy `.env.example` to the Git-ignored `.env.local` to override it,
then restart Vite. Production changes require rebuilding because Vite environment
values are embedded at build time. All `VITE_*` configuration is public; put no
tokens, passwords, signing keys or database credentials in it.

The base includes the version prefix. Supply endpoint paths such as `/villages`,
not `/api/v1/villages`. A base can be an origin-relative path or an absolute
HTTP(S) URL, with optional trailing slash. Base queries, fragments and embedded
credentials are rejected. Endpoints cannot override or traverse outside the base.

- **Development:** Vite currently has no API proxy. `/api/v1` would reach Vite's
  own origin until a proxy is configured against the team's confirmed backend
  address. Alternatively, an explicitly configured absolute API base requires
  backend CORS support for the frontend origin. Neither the backend address nor
  CORS/public-read changes are assumed or configured in this step. Mock village
  browsing makes no API requests; explicit login uses the configured API base
  independently of the village mode.
- **Production:** the intended default is same-origin routing: the web server
  sends `/api/v1/*` to the backend and serves the frontend at other application
  paths. API paths must be excluded from the SPA HTML fallback. A separately
  hosted API needs an explicit build-time base URL and team-approved CORS rules.
  Deployment routing remains infrastructure work.

## Client interface

```js
import { apiClient } from './services/apiClient.js'

const controller = new AbortController()
const villages = await apiClient('/villages', {
  query: { page: 0, size: 6, search: 'بتير', governorateId: 2 },
  signal: controller.signal,
})

const source = await apiClient('/sources', {
  method: 'POST',
  body: { title: 'مرجع', sourceType: 'BOOK' },
  token: callerSuppliedToken,
})
```

These are interface examples, not calls currently made by the application.
Methods default to GET and are uppercased. `body` is a JSON-serializable value;
GET/HEAD bodies are rejected. JSON bodies receive a Content-Type header, and
Accept defaults to `application/json`. Additional `headers` are accepted.

`query` accepts an object or URLSearchParams. Object null/undefined values are
omitted; zero, false and empty strings are retained; arrays become repeated keys.
Supplied query keys replace the same keys already present in the endpoint URL.
Values are URL-encoded, including Arabic text. Unknown parameters are not checked
against individual backend contracts here.

A caller's `token` becomes a Bearer header for that request only. The client has
no token persistence, refresh, login redirect or credential logging. Cookie
credentials are omitted. There are no automatic retries, including for mutations.
`signal` supports cancellation before Fetch, during Fetch and while reading the
response; the original abort reason/error is propagated.

Successful JSON is returned directly, without a response wrapper. Empty or
whitespace-only bodies, 204/205 responses and HEAD responses return `null`.
Nonempty successful bodies must parse as JSON, even if a server omitted its
Content-Type header. HTML SPA fallbacks therefore produce a malformed-response
error rather than silently becoming application data. Endpoint-specific DTO
validation and mapping belong to later service adapters.

`createApiClient({ baseUrl, fetchImpl })` also creates an independent client;
tests inject mocked Fetch implementations through this factory.

## Structured errors

Failures are `ApiError` instances except native cancellation and invalid caller
configuration/body errors. The client does not log them. Inspect:

| Property | Meaning |
| --- | --- |
| `kind` | `http`, `network`, or `malformed-response` |
| `code` | BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, SERVER_ERROR, HTTP_ERROR, NETWORK_ERROR, or MALFORMED_RESPONSE |
| `status`, `statusText` | Received HTTP metadata; null when no response was received |
| `message` | Backend `message`, then string `error`, then a generic status message |
| `details` | Complete parsed error payload, raw non-JSON body, or null |
| `headers` | Received response Headers, when available |
| `retryAfter` | Unmodified Retry-After header: delay-seconds or HTTP-date; no automatic scheduling |
| `malformedBody` | Whether a nonempty response body failed JSON parsing |
| `cause` | Original parsing/transport error, when available |

Both `{timestamp,status,error,message,path}` and data-quality `{error}` bodies
are supported. Empty 401/403/429 bodies still preserve their HTTP status. An HTML
5xx remains an HTTP/server error with its raw body; invalid JSON in a successful
response is a malformed-response error. Network/response-stream failures use
NETWORK_ERROR; a stream failure retains HTTP metadata already received.
Cross-origin response headers such as Retry-After are readable only if the
backend's eventual CORS policy exposes them.

Public read access, publication filtering, `/auth/me` roles, authentication UI,
API enablement, and the missing backend contracts remain separate team
work. This foundation does not make protected or unverified data public.

## Pure data adapters

`src/services/villageAdapters.js` and `src/services/paginationAdapters.js` have
no network, storage or React dependencies. The Explorer service uses them for
API responses; `villageService.js` and its mock slug routes remain unchanged.

The source contracts are the backend's `VillageResponse`, `GovernorateResponse`
and `VillagePageResponse` in `com.qaryati.village.dto`, plus the common envelope
of `PopulationRecordPage` and the heritage `*Page` records. These adapters do not
accept request DTOs or validate unresolved domain-specific response contracts.

```js
import { adaptGovernorateCatalog, adaptVillage, toBackendId } from './services/villageAdapters.js'
import { adaptVillagePage, adaptRecordPage, toBackendPage } from './services/paginationAdapters.js'

// Inputs below are already-decoded DTOs; adapters never fetch them.
const catalog = adaptGovernorateCatalog(governorateDtos)
const village = adaptVillage(villageDto, catalog.namesById)
const villages = adaptVillagePage(villagePageDto, catalog.namesById)
const records = adaptRecordPage(populationOrHeritagePageDto)
const backendId = toBackendId(village.id)
const backendPage = toBackendPage(villages.page)
```

Mapping rules:

| Backend/input | Adapter output |
| --- | --- |
| Positive safe-integer `id` | Canonical decimal string `id` for routes, plus numeric `backendId` |
| `nameAr` | `name` and `nameAr`; `nameEn` retained separately, without an invented Arabic fallback |
| Governorate catalog array | `{items, namesById}`; all catalog rows in supplied order, with numeric Map keys |
| `governorateId` | Retained numeric ID and catalog-derived Arabic `governorate`; unknown/absent name stays null |
| `localAuthorityId` | Retained numeric ID or null; no inferred authority name |
| `latitude`, `longitude` | Retained separately; `coordinates` is `[latitude, longitude]` only when both are present and valid |
| `nameOrigin`, `locationDescription`, `elevationM`, `areaKm2` | Retained under their DTO names; location description is not a historical overview |
| `status` | Exact nullable `VillageStatus`; no inferred public/verified flag or filtering |
| Mock-only content fields | `featured`, `contentStatus`, `overview`, `population`, `history`, `heritage`, `agriculture`, `community` are null |

Optional missing/null fields stay null; blank text is treated as missing. Invalid
types fail with TypeError instead of coercion. Numeric strings are not accepted
as DTO IDs or coordinates. `toRouteId`/`toBackendId` only round-trip positive safe
integers and canonical decimal strings; mock slugs, leading zeros, fractions,
exponents and unsafe Java Long values are rejected. Supporting larger Long IDs
would require a lossless backend wire contract, since ordinary JSON parsing may
already have rounded them. No name-to-slug lookup is inferred from mock data.

`toCoordinates` validates finite numeric latitude in [-90, 90] and longitude in
[-180, 180]. Zero is valid. An incomplete pair returns null while individual
axes remain available on the village; invalid supplied axes throw even when the
other axis is missing. Null never becomes zero. No images or verification
properties are added. Mock cards retain demo artwork; API Explorer cards use
explicit missing-content labels and no fabricated village images.

Both page adapters return `{items, total, page, pageSize, totalPages,
numberOfElements, first, last, hasNext, hasPrevious, empty, outOfRange}`.
`adaptVillagePage` reads `number` and maps village rows using the supplied catalog;
`adaptRecordPage` reads `page` and copies only the content array, leaving domain
records untouched. It does not map population or heritage into village content.
The caller retains ownership of those domain objects.

Backend page 0 becomes frontend page 1; `toBackendPage` performs the inverse.
Empty datasets retain `totalPages: 0`, rather than inventing an extra page.
Out-of-range responses keep the actual requested page and empty items, with
`outOfRange: true` (except the normal first page of an empty dataset). Any later
navigation correction must fetch the desired page before presenting its data;
the adapters never clamp or fetch. Counts, page sizes, totals, village flags and
content lengths must be internally consistent; malformed/missing envelopes are
errors, not empty datasets.

Governorate catalog availability is separate from the villages on a single page.
Missing catalog entries remain null, with their original governorate IDs retained.
Duplicate governorate IDs fail explicitly; duplicate names remain distinct rows.
Adapters do not provide publication security: DRAFT/UNDER_REVIEW/VERIFIED/REJECTED
rows are mapped as supplied. Backend public access/publication policy, role-aware
village requests, live API enablement, images and unresolved domain mappings
remain future work. There is no client-side security filtering.

Interactive browser verification was unavailable during adapter validation:
both browser tools failed to connect to the native computer-use pipe. Automated
mock-service tests and HTTP route checks passed, but interactive UI behavior
was not verified in a browser.

## Village Explorer API integration (gated)

`VITE_VILLAGE_EXPLORER_MODE=mock` is the shared Home/Map/Explorer/Details default. `api` selects the API path;
unknown mode values show an explicit configuration error. Neither errors nor
blocked API mode silently select mock data. This setting applies only to
Home, Map, Explorer and Details. Authentication, gated Sources and the protected
VerificationPanel, data-conflict views and the read-only audit log page use the API
base independently.

The shared source-level `publicVillageContractsVerified` gate in `villageMode.js`
remains **false**. Setting the mode to `api` alone shows an unavailable state
and makes **no requests**, including governorate requests. No environment flag
can approve publication security. Tests inject `publicationVerified: true`
into isolated service factories using mocked HTTP; the app singleton uses the
closed source-level gate. This is a release gate, not an authorization boundary.

The latest fetched `origin/main` had no incoming backend contract changes.
Reviewed backend evidence still blocks public enablement:

- `SecurityConfig` requires authentication except for POST login.
- `VillageController` requires VIEWER/EDITOR/ADMIN for collection reads.
- `VillageService.activeSpecification` always excludes archived rows, but does
  not enforce a public publication-status restriction. All village statuses are
  readable by existing read roles.
- Village identity reads use `getById` without an archive/publication restriction;
  a public Details policy is also required before enablement.
- `GovernorateController` returns `List<GovernorateResponse>` and is covered by
  the authenticated request rule.

Re-enabling the application API path requires a new source review of merged
backend public-read authorization and enforced publication rules, then an
explicit change to the source-level gate. Sending `status=VERIFIED` or hiding
rows in the browser is not a security substitute. These changes add neither.
The API base remains `/api/v1`; no backend host/port or proxy is assumed.

`createVillageExplorerService` exposes `listGovernorates({signal})` for the API
catalog and `listVillages({query, governorateId, page, pageSize, sortBy, sortDir,
signal})`. Only GET `/governorates` and GET `/villages` are called, through the
existing API client. Governorate selection uses canonical decimal ID strings;
its catalog is independent of the current village page. Unknown IDs and empty
datasets remain legitimate empty results. Catalog failures fail the operation
explicitly instead of fabricating names or substituting mock data.

Explorer URL state uses `q`, `governorateId`, one-based `page`, `sortBy` and
`sortDir` in API mode. Page size defaults to 6; service sizes 1–100 and backend
offset limits are validated before HTTP. The backend's five allowed sort fields
are exposed in Arabic controls. Filtering and sorting are sent to the server;
response rows are not filtered or sorted in the browser. Mock mode keeps its
existing `governorate` name parameter and mock behavior. Legacy mock-name filters
without a numeric ID fail explicitly in API mode rather than silently broadening
the query. Choosing a governorate removes the alternate source's filter.

Filter/sort changes reset `page`; navigation, page correction and clearing
filters preserve unrelated/repeated URL parameters. Out-of-range responses
trigger a real request for the last valid page (page zero for an empty dataset).
Two corrective reads are allowed for concurrent dataset shrinkage; continued
changes produce a retryable error. A failed correction never displays data under
a falsely clamped page number. Network/HTTP errors have no automatic retries.

`useExplorerData` uses an abort/stale-response controller also reused by API Details;
the mock hook is unchanged. Navigation and unmount abort the entire catalog/list
operation, including corrections. Old success/error responses are ignored even
if a transport ignores cancellation. URL correction is guarded against newer
navigation and reuses only the matching, already-fetched response. Loading,
empty, unavailable, 401, 403, rate-limit and network errors have Arabic states.
Village services do not receive session tokens or redirect to login; their
publication gate remains closed independently of the new authentication service.

API cards preserve the existing RTL layout and colors, show explicit missing
name/governorate/image labels. Numeric Details links require available, matching
Explorer and Details integrations; API villages never link to mock Map routes.
Interactive browser verification was attempted again
for this integration and remained unavailable because the native computer-use
pipe could not connect. Mocked HTTP tests do not establish interactive UI coverage.
Live backend verification also remains unavailable: no backend was started and
no live API requests were made. Public-read authorization and publication rules
remain unconfirmed, so the application API gate stays closed.

## Village Details API integration (gated)

Details reuses the existing mode variable and the same closed release gate as
Explorer. The gate was moved into `villageMode.js`, remains **false**, and covers
identity, governorates and all four heritage collections. Public-read and
server-side publication rules for every integrated endpoint must be reviewed
before any future enablement. Existing authenticated VIEWER filtering of heritage
records is not proof of a public-read contract. No frontend status filtering is
used as an authorization or publication boundary.

`villageDetailsService.js` calls only the following GET contracts through the
existing API client (paths are relative to the configured `/api/v1` base):

| Path | Purpose / parameters |
| --- | --- |
| `/villages/{id}` | Numeric identity and geography; no query parameters |
| `/governorates` | Independent catalog and numeric-ID-to-Arabic-name mapping |
| `/village-historical-overviews` | `villageId`, zero-based `page`, `size` |
| `/historical-events` | `villageId`, zero-based `page`, `size` |
| `/cultural-heritage` | `villageId`, zero-based `page`, `size` |
| `/heritage-sites` | `villageId`, zero-based `page`, `size` |

Route IDs must be canonical positive safe-integer decimal strings in API mode.
Invalid/sluggified IDs make no HTTP requests; the mock branch keeps its existing
slug behavior. A 404 from the identity endpoint returns a missing village and
does not trigger child requests. A 404 from a section remains a section error.
Mismatched identity IDs, malformed DTOs and wrong-village collection rows fail
explicitly; records are never selectively hidden to mask a broken contract.

`heritageAdapters.js` follows `VillageHistoricalOverviewResponse`,
`HistoricalEventResponse`, `CulturalHeritageCrudResponse`, `HeritageSiteResponse`
and their enum definitions. It preserves descriptions, historical LocalDate
strings (including signed historical years), year ranges, date precision,
Instant strings, source IDs and each record's verification status. There is no
timezone conversion or fabricated January/day value for year/period records.
Missing values remain null, and absent source information stays distinct from
an explicit empty source list. Coordinates use the existing finite/range checks;
null coordinates never become zero. DTO text is rendered as React text, not HTML.

Identity loads first. Governorate names and all four collections then use
independent instances of the existing cancellation/stale-response hook. A catalog
or section failure leaves the identity and successful sections usable, shows an
error instead of an empty state, and can be retried independently. Sections have
separate one-based UI pagination (default size 5) and request only the selected
village's page. Out-of-range pages fetch the last valid page, with at most two
corrective reads; failed corrections remain errors. No entire collections are
downloaded for local filtering. Navigation resets section pages, aborts old
requests, ignores late completions, and updates the document title for loading,
selected village, missing-name, not-found and failure states.

API Explorer cards link to numeric Details routes only when both services are
available in the same mode. Their return link retains the Explorer pathname and
query parameters through router state; other return destinations are rejected.
API Details links valid coordinates to the API Map only when both integrations
are available in the same mode. Mock Details keeps its original page structure,
demo text, titles, section values and Map links. Home and Map use the same mode;
their mock behavior is preserved.

Population, agriculture and community are explicitly unavailable in API Details:

- `PopulationRecordController.getAll(page, size)` still has no `villageId`
  parameter. No unfiltered population collection is downloaded.
- Agriculture's village lookup and community's village filters exist, but their
  read services have no approved server-side publication policy. Their endpoints
  are not called and their domain adapters are not invented here.
- The latest fetch had no incoming backend changes. Global authentication and
  unconfirmed village/publication policy still prevent live public enablement.

Automated tests use mocked HTTP and dependency-free Node assertions, covering
domain/null mappings, request paths, filters, pagination, independent failures,
closed gates, cancellation/stale responses, titles, mock regressions and navigation
helpers. Interactive browser verification was attempted for Details but the native
computer-use pipe was unavailable. Interactive UI behavior remains unverified.
HTTP application-shell checks are not browser verification. No backend was started,
no live backend requests were made and live backend verification remains unavailable.

## Map and Home API integration (gated)

The existing mode variable now applies to all four main pages. Its name is kept
for configuration compatibility. The shared publication gate remains **false**;
selecting `api` does not approve or enable public data access. Map's closed gate
prevents every catalog/list request and makes no mock fallback. No authentication,
publication-policy change or new backend endpoint is introduced.

`villageMapService.js` traverses the verified village listing through the existing
Explorer service and adapters. Each call requests one-based page N as backend
page N-1, with `sortBy=id`, `sortDir=asc`, and default size 100. The reused service
also loads the governorate catalog for each page; no separate transport logic or
hidden endpoint is added. Traversal is sequential and bounded to 100 pages and
10,000 records. Smaller factory limits are supported for tests; values above
these ceilings are rejected before HTTP.

The first page establishes total records/pages. Subsequent responses must keep
those totals, return the requested page/size and contain unique IDs in strictly
ascending order. A changed total, duplicate ID, skipped/corrected page, malformed
response, failed request or limit overrun rejects the entire load. Already-read
rows are never returned as a partial map, silently deduplicated, or represented
as complete. Retry starts from page zero with a fresh collection. An empty first
page with zero totals is a successful complete empty traversal.

`complete: true` means every advertised page was retrieved and the observable
counts/IDs/order checks passed. The backend uses offset pagination and has no
snapshot/version token; these checks cannot establish a point-in-time snapshot
or detect all same-count changes during concurrent edits. A stronger consistency
guarantee requires a backend contract. This limitation remains separate from the
closed publication gate.

The API Map renders only after successful full traversal. Coordinate validation
comes from the existing village adapter. Malformed supplied coordinates fail
the load; nullable/incomplete pairs remain null and stay in the village index
without markers. Actual numeric zero is valid, and missing axes never become
zero. Marker eligibility is geography-only, not client-side publication filtering.
Numeric `village` selections are resolved against the loaded permitted listing;
invalid, absent and missing-coordinate selections have explicit states. No direct
identity lookup is used to add a village omitted from the listing.

Map, Explorer and Details links require matching available integrations. API
popups/index links use numeric Details routes; router state preserves a Map return
URL including selected-village and unrelated query parameters. Only internal Map
or Explorer return paths are accepted. Mock slug navigation remains separate.
Selection changes reuse the complete dataset; reload/unmount cancels the entire
traversal and the existing stale-response controller ignores late success/errors.

Leaflet, its marker assets and Arabic controls, OpenStreetMap tiles and attribution,
RTL popups/layout and the original mock Map rendering are retained. Tile failures
keep the existing warning and village index available. Empty collections, no valid
locations, missing names, HTTP/auth failures and network/traversal failures have
explicit Arabic states. No partial dataset is shown after a failed load.

`villageHomeService.js` reuses the existing Explorer service for mock featured
villages, preserving the three demo selections and their order. Home search uses
the Explorer URL helper and the `q` parameter, preserving literal Arabic and
reserved characters without adding status filters. The hero, Arabic text,
illustrative artwork, routes and mock cards are preserved.

No featured endpoint or approved selection policy was found in the fetched
backend contracts. In API mode, `getFeatured` returns an explicit unavailable
state and makes no requests, even in isolated tests with publication confirmation
injected. It never sends an unsupported featured parameter, takes arbitrary rows
from the listing or substitutes demo records. This availability-only result does
not bypass the gate or expose API data. A future featured policy needs separate
backend/team approval before implementing a selection.

Dependency-free tests cover all-page retrieval, incomplete/empty datasets, limits,
duplicates, coordinate validity, cancellation/stale loads, selections, navigation,
Home search, featured unavailability, mock regression and zero-request closed gates.
Interactive verification of Home/Map was attempted, but the native computer-use
pipe could not connect. Browser layout, marker/popup/zoom interaction and tile-error
rendering remain unverified interactively. HTTP application-shell checks are not
browser verification. The backend/PostgreSQL were not started, no live API requests
were made and live backend verification remains unavailable.

## Authentication and session management

Reviewed the fetched backend authentication DTOs, controller, rate-limit filter,
security chain, JWT issuer/validator and database-backed authority converter.
There were no incoming backend changes relative to this branch. Verified routes
(relative to the existing API base):

| Request | Contract |
| --- | --- |
| POST `/auth/login` | JSON `{username, password}`, both NotBlank; returns `{accessToken, tokenType: "Bearer", expiresIn}` with duration in seconds |
| GET `/auth/me` | Bearer token; returns `{username}` only |

Credentials are sent verbatim after required-field validation. No extra password
policy, refresh endpoint, logout endpoint, synthetic account or demo session is
introduced. Login is never retried automatically. Existing `ApiError` status,
details, network cause and `Retry-After` are preserved by the service. The Arabic
UI handles 400, 401, 403, 429 and network failures without echoing raw server text.
The backend rate limiter can return an empty 429 with `Retry-After: 60`.
Delay-seconds and HTTP-date headers disable manual resubmission until their
deadline; expiry of the countdown does not send another login request.

`authSession.js` keeps the token private in memory. `AuthProvider` and `useAuth`
expose identity, status, expiration, login/logout, profile refresh and a scoped
authenticated request method; they do not expose the token. Authentication is
complete only after `/auth/me` succeeds. Expiration uses the returned `expiresIn`
from the start of the login request, conservatively accounting for transit time.
Timers, focus/visibility checks and pre-request checks expire sessions; large
durations use bounded timer chunks. Reloading the app loses the session. There
is no localStorage, sessionStorage, cookie or IndexedDB token persistence.

Logout, expiry, provider cleanup and confirmed current-session 401 responses
clear identity/token and abort pending work. A 403 denotes insufficient permission,
and a network failure does not prove that an established session is invalid.
Those profile errors retain the known identity with an explicit error until
expiry/logout. Failed initial profile verification leaves the app anonymous.
Concurrent login submissions are rejected; obsolete profile requests are aborted.
Generation checks prevent late login/profile/request responses, including an old
401 after a new login, from restoring or invalidating the current session.
Passwords and tokens are never logged or embedded in URLs/configuration.

`/login` provides required fields, password visibility, pending/cancel states,
retry countdowns and accessible Arabic messages. Navbar shows the verified profile
username and client-side logout after authentication. Public Home, Explorer,
Details and Map remain unguarded, retain their existing renderers and default to
mock data. Authentication does not attach credentials to village services, change
the publication gate or approve live public reads. No requests occur on anonymous
startup; auth requests begin only through explicit login/session operations.

`RequireAuth` is available for future protected routes, with loading, login
redirect and safe internal return navigation. No administrative route is added.
**Effective roles remain blocked:** `/auth/me` still omits them. The JWT has no
role claims; the server resolves authorities from the database. Profile mappings
therefore expose `roles: null, rolesVerified: false`, ignore unsupported role
properties and deny every role-dependent guard requirement. No username or JWT
decoding is used to infer permissions. Frontend guards control UI only; backend
authorization remains authoritative.

The API base is shared with the existing client and is independent of the village
mode/publication gate. No backend port or proxy has been assumed. Before live
authentication can be verified, the team must confirm backend routing and, for a
separate origin, applicable CORS and `Retry-After` response-header exposure. No
backend/PostgreSQL was started and no real users were created.

Validation: 515 dependency-free automated tests, production build and ESLint
passed. Interactive browser verification was attempted but the computer-use native
pipe was unavailable. HTTP 200 app-shell checks for Login, Home, Explorer, Details
and Map do not establish interactive verification. Live authentication remains
unverified; mocked HTTP coverage does not confirm deployment routing or CORS.

Manual verification once a browser and confirmed test backend are available:

1. Check Login in RTL on desktop/mobile and by keyboard: labels, required-field
   errors, focus, password toggle, pending/cancel and duplicate submission.
2. Using an existing authorized test account, verify the POST DTO, Bearer `/me`,
   profile username and logout; check that tokens stay out of persistent storage,
   URLs and console output. Reload should return to anonymous.
3. Check invalid credentials, 403, offline/network failure and 429 countdown;
   confirm one POST per submission and no automatic retry after the countdown.
4. Check expiry, logout during pending requests and navigation away from Login;
   late responses must never restore a terminated session.
5. Check Home, Explorer, Details and Map mock navigation before/after login, and
   verify that API village mode remains blocked with zero village API requests.

## Sources management and village source linking (role contract blocked)

Fetched remote refs on `roaa/frontend-integration` at `93669e7`; there were no
incoming `origin/main` changes. Reviewed `SourceController`,
`VillageSourceController`, their services/entities, source DTOs and enum,
`DataQualityExceptionHandler`, global handlers and the security configuration.
`/auth/me` still returns only username, without effective roles.

**Every Sources read is role-dependent.** Controller and service rules require
VIEWER/EDITOR/ADMIN for all four GET contracts and EDITOR/ADMIN for mutations.
There is no Sources endpoint explicitly permitting every authenticated user.
Consequently, the actual app makes **zero Sources requests**, including catalog
and link reads. `/sources` uses `RequireAuth`, then shows an explicit unavailable
state instead of an empty catalog or fabricated records. The authenticated Navbar
links to this protected route; Login preserves safe `/sources?source=ID` returns.

`sourceAccess.js` keeps `effectiveSourceRolesVerified` false independently of the
village publication gate. There is no environment switch or demo role/session.
Unexpected role properties, usernames and JWT decoding cannot enable access.
Future enablement requires a reviewed effective-role DTO, corresponding auth
mapping/guard updates, and explicit source-policy review. Isolated automated tests
inject verified-role fixtures into factories to exercise the verified endpoint
contracts; those fixtures never enable the application singleton or create users.
Frontend permissions control UI behavior; backend authorization is authoritative.

`sourcesService.js` uses AuthProvider's scoped request method and the unchanged
API client. Verified paths (relative to the existing `/api/v1` base):

| Method/path | Request / response |
| --- | --- |
| GET `/sources` | `SourceResponse[]`, no search or pagination parameters |
| GET `/sources/{id}` | One `SourceResponse` |
| POST `/sources` | `CreateSourceRequest`; 201 with `SourceResponse` |
| PUT `/sources/{id}` | Full `CreateSourceRequest` replacement; `SourceResponse` |
| GET `/villages/{villageId}/sources` | `VillageSourceResponse[]` |
| POST `/villages/{villageId}/sources` | `{sourceId, claimNote}`; 201 with `VillageSourceResponse` |
| GET `/sources/{sourceId}/villages` | `VillageSourceResponse[]` |

`CreateSourceRequest` contains exactly `title`, `publisher`, `author`, `sourceType`,
`url`, `publicationDate`, `accessedAt`, and `citation`. Title is NotBlank, maximum
350 UTF-16 code units; publisher/author are nullable, maximum 200. Source types
are CENSUS, GOVERNMENT, BOOK, ARTICLE, ARCHIVE, WEBSITE, INTERVIEW and OTHER.
URL is nullable and must satisfy backend HTTP(S) URL validation. Dates are nullable
Java LocalDate strings, including canonical signed historical years. Citation and
claim note have no invented text-length constraint. Server validation remains
authoritative. Responses additionally preserve numeric source ID and nullable
`createdAt` OffsetDateTime text. No verification status or missing metadata is
invented. URLs and free text are rendered as text, without HTML or automatic fetches.

Adapters preserve null, empty text, whitespace, exact dates/timestamps and notes.
They reject malformed envelopes, unknown enums, unsafe IDs, duplicate catalog
IDs/links and wrong-identity responses. Explicit nullable fields are required in
full source responses; missing fields cannot become accidental PUT clears.
Update bodies merge only deliberately changed form fields over a complete,
validated detail baseline, preserving every untouched optional field. Clearing a
field sends explicit null. IDs and creation timestamps are never PUT fields.
The backend has no exposed version/ETag concurrency contract, so this preserves
the loaded baseline, without guaranteeing protection against concurrent editors.

Reusable source details/forms, village-source listing/link selection and reverse
source-village listing use the actual DTOs. Numeric route IDs must be canonical
positive safe integers; request bodies use numeric IDs. Village-specific panels
require a real matching village ID and an available API integration. Mock slugs
are rejected, and no arbitrary village-ID input or invented village names are
provided. Reverse links show IDs and notes; navigation to public Details remains
unavailable while its API integration is gated. Public village pages are unchanged.

Reads have separate loading, empty, forbidden, not-found and network-error states.
HTTP 400/401/403/404/409 and network semantics remain intact. A current-session 401
clears authentication through the existing session layer; 403 remains a permission
failure. A duplicate link 409 leaves the entered and existing information intact
and never triggers a PUT or overwrite. Mutations have submission latches, disable
form controls while pending and are never automatically retried. An uncertain
network/write result advises checking data before manually submitting again;
transport cancellation cannot roll back a server operation already committed.

AuthProvider exposes its existing read-only public snapshot getter for scope
checks, without exposing tokens. Each Sources service is bound to the verified
profile object and expiration; logout, expiry, replacement sessions or profile
changes invalidate old services. Existing read cancellation/stale-response handling
is reused, and mutation callbacks are suppressed after cancellation or scope
changes. Tokens remain in memory and are never logged or persistently stored.

Interactive browser verification was attempted for Sources but the computer-use
native pipe was unavailable. Interactive layout, forms and navigation remain
unverified. Backend routing/CORS is still unconfirmed; live authentication and
Sources verification remain unavailable. No backend/PostgreSQL was started and
no real source/link records were created. The shared village publication gate
remains closed and public mock browsing remains the default. No deletion,
unlinking, search, pagination, Verification Records, Data Conflicts or Audit Logs
feature is added.

Manual checks once browser/routing are available: verify anonymous `/sources`
redirect and safe login return, keyboard/RTL behavior, authenticated unavailable
notice with zero Sources requests, logout/navigation cleanup and unchanged public
mock browsing. Live catalog/forms/link checks require the effective-role contract
and real-village integrations to be reviewed first; do not bypass the gates.

Validation for this step: all 583 dependency-free tests passed (68 added), along
with `npm run build`, `npm run lint`, `git diff --check` and whitespace checks of
all new files. Coverage includes exact HTTP contracts/Bearer headers, optional-field
preservation, validation/IDs, role restrictions and zero-request missing-role
behavior, conflicts, errors, cancellation, logout/session changes, stale success
and failure, duplicate submissions, safe login returns and existing mock regression.
These automated checks do not establish interactive browser or live backend coverage.

## Verification records (protected API Details panel)

Started from clean `roaa/frontend-integration` at `eaa4ac8`, fetched remote refs,
and found no incoming `origin/main` changes. Reviewed the verification controller,
service, request/response DTOs, enum, entity/repository, data-quality exception
handlers and security configuration. `/auth/me` still exposes only username.

Verified paths, relative to the existing `/api/v1` API base:

| Request | Contract / authorization |
| --- | --- |
| GET `/verifications?entityType=VILLAGE&entityId={id}` | `VerificationRecordResponse[]`; every authenticated user is explicitly permitted by both controller and service |
| POST `/verifications` | `{entityType, entityId, decision, reason}`; 201 with one `VerificationRecordResponse`; ADMIN only |

Only VILLAGE is supported. Entity type is NotBlank, maximum length 80, trimmed by
the backend. Entity ID is a positive Long; frontend routes accept only canonical
positive safe-integer decimal strings and send numeric IDs. Unsafe JSON Long IDs
and mock slugs are rejected. Supported decisions are VERIFIED, REJECTED and
NEEDS_REVISION. Reason is nullable free text, with no invented required-field or
length restriction. No update/delete/approval endpoints or pagination are exposed.

Response fields are `id`, `entityType`, `entityId`, `reviewerUserId`, `decision`,
`reason` and `reviewedAt`. Adapters validate identity/decision, reject malformed,
duplicate or wrong-village records, and preserve backend array order without
client sorting. The repository orders by reviewedAt descending; tie ordering is
not strengthened in the browser. Exact reason text, null/empty values, numeric
reviewer IDs and actual OffsetDateTime timestamps (including offset/fractional
seconds) are preserved. Missing reviewer/time metadata stays unavailable; no
reviewer name, current username or browser-generated timestamp is substituted.

Creation records a verification decision and its audit entry. The reviewed service
does **not** update village status. The panel explicitly describes that behavior;
it neither changes local village status nor sends a village update request.

`verificationService.js` uses AuthProvider's existing scoped request/getSnapshot
architecture and unchanged API client. Authentication-only read permission reuses
`authAccessDecision`; ADMIN creation reuses the existing verified-role helper and
its still-closed effective-role contract. Missing roles do not block the verified
GET contract, but **all actual application POSTs remain blocked**. No environment
override, inferred JWT claims, synthetic users or administrator sessions are added.
Tests inject isolated role fixtures only into service factories.

The reusable Arabic RTL `VerificationPanel` mounts in `ApiVillageDetails` after
a real village identity loads. It reuses the numeric-identity/integration guard;
unavailable API integrations, mismatched IDs and mock slugs cannot load protected
history. Anonymous users see a Login link with a safe internal return URL. The
mock Details renderer does not mount the panel. The shared public publication
gate remains false, so actual app history reads also remain unavailable until
real API village identity integration is approved and available. No manual-ID
lookup bypass or protected data is added to public mock browsing.

History has separate loading, empty, forbidden and error states, with explicit
unavailable metadata labels. Administrator-only form logic supplies accessible
decision/reason labels, validation, pending state and duplicate-submission
protection. The actual form is unavailable while effective roles are missing.
No mutation is retried automatically. After confirmed creation, the created
record remains a success while history reloads independently. Refresh failure
shows a separate history error and a GET-only retry; it never reclassifies the
POST as failed or automatically submits another decision. A failed/unconfirmed
POST reports its own error and does not overwrite the existing history.

The panel state controller reuses existing latest-read and mutation controllers.
Village navigation/unmount cancels pending work and clears protected state.
Session/profile replacement, logout and expiry invalidate old services, redact
snapshots and suppress obsolete read/mutation callbacks. Existing auth handling
clears confirmed current-session 401 failures and preserves the distinction from
403 permission failures. No tokens are exposed, logged or persistently stored.
Transport cancellation cannot undo a server mutation already committed; an
uncertain write result advises checking history before manually retrying.

Remaining enablement blockers are effective roles for ADMIN creation, confirmed
backend routing/CORS, and approved real-village API integration. These frontend
changes do not alter backend authorization or the public publication gate.

Validation: all 650 dependency-free tests passed, including 67 new verification
tests. `npm run build`, `npm run lint`, `git diff --check` and whitespace checks
for every new file passed. Tests cover exact query/body/Bearer contracts, entity
types/decisions/IDs, nullable metadata, response ordering, role restrictions,
confirmed creation followed by refresh failure, duplicate submissions, status
semantics, cancellation, logout/session replacement and existing regressions.

Interactive browser verification was attempted but the computer-use native pipe
could not connect. HTTP 200 app-shell checks do not establish interactive UI
verification. Live authentication and verification API validation remain
unavailable; no backend/PostgreSQL was started and no real verification records
or users were created. No commit, push or merge was performed in this step.

Manual verification once browser and approved test integration are available:

1. Confirm mock Details, Home, Explorer, Map, Login and Sources navigation remain
   functional, with no verification panel/data or verification requests in mock
   browsing. Selecting gated API mode must still make zero public-data requests.
2. After real-village integration is approved, check a known numeric village
   identity: anonymous users should see Login, and an existing authenticated test
   account should read only the selected village's history without needing roles.
   Missing roles must leave the administrator form unavailable.
3. Check Arabic RTL layout, keyboard/focus behavior, labels, loading/empty/error
   states, actual reviewer IDs/timestamps, missing metadata and server ordering.
4. Use isolated mocked POST responses (or an explicitly approved test setup after
   roles are exposed) to check ADMIN-only decisions, duplicate clicks and success
   followed by a failed GET refresh. A history retry must issue only GET; village
   status must remain unchanged.
5. Check 400/401/403/404 and offline responses, then navigate between villages or
   log out with delayed requests. Old history, saved records and errors must not
   appear in the new village/session. Never bypass the application gates to test.

## Data conflicts (role contract partially blocked)

Reviewed `DataConflictController`, `DataConflictService`, the `DataConflict`
entity, `CreateDataConflictRequest`, `ResolveDataConflictRequest`,
`DataConflictResponse`, `DataConflictStatus`, `DataQualityExceptionHandler`,
`SecurityConfig` and the controller integration tests. No incoming `origin/main`
changes were present.

| Endpoint | Contract | Backend authorization |
| --- | --- | --- |
| GET `/data-conflicts?entityType=VILLAGE&entityId={id}` | `DataConflictResponse[]`, createdAt desc | authenticated |
| GET `/data-conflicts/status/{status}` | `DataConflictResponse[]`, createdAt desc | EDITOR or ADMIN |
| POST `/data-conflicts` | `{entityType, entityId, fieldName, conflictingValues}`; 201 | EDITOR or ADMIN |
| PATCH `/data-conflicts/{id}/review` | no body; OPEN to IN_REVIEW | ADMIN |
| PATCH `/data-conflicts/{id}/resolve` | `{resolutionNotes}`; IN_REVIEW to RESOLVED | ADMIN |

Only `VILLAGE` is supported; the read compares it exactly. `fieldName` is required
non-blank text of at most 100 characters (the server trims it).
`conflictingValues` needs at least two non-empty maps with non-blank keys and
non-null values; nested JSON values are allowed. Resolution notes are required
non-blank text with no length limit. Invalid transitions return 409, validation
400, a missing village or conflict 404, missing roles 403 and a missing session
401. Resolution sets only the conflict's status, notes, `resolvedBy` and
`resolvedAt`. It does **not** change village data, and the UI states this.

`conflictService.js` reuses AuthProvider's scoped request, the API client and
the session checks used by Verification. `conflictAccess.js` reuses the closed
effective-role contract. Authenticated users can read village conflicts. Status
lists, creation, review and resolution are blocked without verified roles. With
the current `/auth/me` contract, the app sends **no** management or mutation
requests. Tests use isolated role fixtures only.

`conflictListState.js` drives both the reusable `VillageConflictsPanel`, which
mounts in `ApiVillageDetails` after the Verification panel, and the protected
`/data-conflicts` page, which uses `RequireAuth` and a `?status=` filter that
defaults to OPEN. It reuses the latest-read and single-mutation controllers.
Only the next valid lifecycle action for the latest server-confirmed status is
offered or sent. One mutation runs at a time, and mutations are never retried.
A confirmed mutation stays successful when the following GET refresh fails. A
village change, filter change, unmount, logout or session change cancels work
and redacts state. Mock slugs are rejected and mock browsing mounts no panel.
The publication gate stays closed.

Competing values render through `StructuredValue.js` as React text nodes. Null,
empty values and over-deep nesting have explicit labels, and no HTML injection
API is used. Server order and nullable metadata are preserved. Form drafts keep
typed text exactly, and `Object.fromEntries` keeps a `__proto__` key as plain
data.

Validation: 710 tests passed (60 new conflict tests). `npm run build`,
`npm run lint` and `git diff --check`, including intent-to-add for untracked
files, passed. Headless Chrome against `vite preview` confirmed that Home and
mock Details render with no conflict panel, and that anonymous
`/data-conflicts` and `/sources` redirect to Login. Authenticated UI, live API
behaviour and role-dependent views remain unverified: no backend or PostgreSQL
was started and no records or users were created.

## Audit logs (read-only, role contract blocked)

Reviewed `AuditLogController`, `AuditLogService`, `AuditLogResponse`, the
`AuditLog` entity, `AuditLogRepository`, `AuditAction`, the `audit_logs` schema,
`DataQualityExceptionHandler` and the controller integration tests. `/auth/me`
still returns only `username`. No incoming `origin/main` changes were present.

| Endpoint | Parameters | Response |
| --- | --- | --- |
| GET `/audit-logs/entity` | `entityType` (non-blank, exact match), `entityId` (positive Long) | `AuditLogResponse[]` |
| GET `/audit-logs/actor/{actorUserId}` | positive Long | `AuditLogResponse[]` |
| GET `/audit-logs/action/{action}` | CREATE, UPDATE, DELETE, VERIFY, REJECT or RESTORE | `AuditLogResponse[]` |

Every endpoint requires `hasRole('ADMIN')` on both the controller and the
service. Results are ordered by `occurredAt` descending. There is no pagination,
global listing, date range, combined filter or mutation; POST, PUT and DELETE
return 405. Invalid IDs or actions return 400, a missing session 401 and a
non-ADMIN user 403. Response fields are `id`, `actorUserId` (nullable),
`actorLabel`, `entityType`, `entityId`, `action`, `occurredAt`, `beforeData` and
`afterData` (nullable JSON maps), `correlationId` and `reason` (both nullable).

Entity types are free strings on the backend. Services currently write
CULTURAL_HERITAGE, DATA_CONFLICT, HERITAGE_SITE, HISTORICAL_EVENT,
POPULATION_RECORD, SOURCE, VERIFICATION_RECORD, VILLAGE_HISTORY and
VILLAGE_SOURCE. For VILLAGE_SOURCE, the entity ID is the village ID. The page
offers these as suggestions only. Matching `AuditLogService.record` and the
`VARCHAR(80)` column, any non-blank type of at most 80 characters is accepted
and sent exactly as entered, so historical or other stored types stay
searchable. Entity results cover only that type and ID, and the page says they
are not a complete village history.

`auditService.js` reuses AuthProvider's scoped request, the API client and the
session checks. `auditAccess.js` requires a verified ADMIN through the existing,
still-closed effective-role contract, so the actual app sends **zero** audit
requests. The protected `/audit-logs` page uses `RequireAuth`. It keeps exactly
one query mode in the URL (`mode=entity|actor|action`) and validates it before
any request. Invalid URL filters send nothing. `auditLogState.js` holds one
read-only store per canonical query. A filter change, leaving the page, logout
or a new session cancels the read and clears audit data.

Records show every returned field. Null metadata is labelled unavailable;
actor names, timestamps and change details are never invented. Snapshots render
through the shared `StructuredValue` text renderer. Server order is preserved.
The login return allowlist adds only `/audit-logs`. To reuse the JSON-shape
checks, `conflictContracts.js` now exports `plainObject` and `jsonValue`; their
behaviour is unchanged.

Validation: 739 tests passed (29 new audit tests). `npm run build`,
`npm run lint` and `git diff --check`, including intent-to-add for untracked
files, passed. Headless Chrome against `vite preview` checked page titles:
Home, Explorer, mock Details, Map and Login render normally, and the 404 page
renders. Anonymous `/audit-logs?mode=action&action=CREATE`, `/data-conflicts`
and `/sources` redirect to Login, and no audit or conflict UI appears in mock
browsing. Authenticated and live API audit behaviour remain unverified.
