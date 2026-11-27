# Qaryati frontend

The React/Vite app defaults to the existing mock data through
`src/services/villageService.js`. Village Explorer now has a gated API service;
Home, Village Details and Map continue to use the unchanged mock service.
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

`VITE_VILLAGE_EXPLORER_MODE=mock` is the default. `api` selects the API path;
unknown mode values show an explicit configuration error. Neither errors nor
blocked API mode silently select mock data. This setting applies only to
Explorer; Home, Details, Map, authentication and data-quality pages are not
integrated with the API.

The source-level `publicContractsVerified` gate in `villageExplorerService.js`
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

`useExplorerData` uses a separate abort/stale-response controller; the hook used
by other pages is unchanged. Navigation and unmount abort the entire catalog/list
operation, including corrections. Old success/error responses are ignored even
if a transport ignores cancellation. URL correction is guarded against newer
navigation and reuses only the matching, already-fetched response. Loading,
empty, unavailable, 401, 403, rate-limit and network errors have Arabic states;
there is no token handling, login redirect or authentication implementation.

API cards preserve the existing RTL layout and colors, show explicit missing
name/governorate/image labels, and do not link numeric IDs into the unintegrated
mock Details/Map routes. Interactive browser verification was attempted again
for this integration and remained unavailable because the native computer-use
pipe could not connect. Mocked HTTP tests do not establish interactive UI coverage.
Live backend verification also remains unavailable: no backend was started and
no live API requests were made. Public-read authorization and publication rules
remain unconfirmed, so the application API gate stays closed.
