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
  CORS/public-read changes are assumed or configured in this step. Mock mode
  makes no API requests.
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
rows are mapped as supplied. Backend access/publication policy, auth and RBAC,
live API enablement, other page integrations, images and unresolved domain mappings
remain future work. There is no client-side security filtering.

Interactive browser verification was unavailable during adapter validation:
both browser tools failed to connect to the native computer-use pipe. Automated
mock-service tests and HTTP route checks passed, but interactive UI behavior
was not verified in a browser.

## Village Explorer API integration (gated)

`VITE_VILLAGE_EXPLORER_MODE=mock` is the shared Home/Map/Explorer/Details default. `api` selects the API path;
unknown mode values show an explicit configuration error. Neither errors nor
blocked API mode silently select mock data. This setting applies only to
Home, Map, Explorer and Details. Authentication and data-quality pages are not
integrated.

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
empty, unavailable, 401, 403, rate-limit and network errors have Arabic states;
there is no token handling, login redirect or authentication implementation.

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
