# Qaryati frontend

The existing React/Vite pages still use `src/data/villages.js` through
`src/services/villageService.js`. The API client foundation is separate and is
not imported by the pages or mock service. Starting the frontend does not start
the backend or connect to PostgreSQL.

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
  CORS/public-read changes are assumed or configured in this step. The untouched
  mock pages make no API requests.
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
live village adapters, and the missing backend contracts remain separate team
work. This foundation does not make protected or unverified data public.

## Pure data adapters

`src/services/villageAdapters.js` and `src/services/paginationAdapters.js` have
no network, storage or React dependencies. They are not imported by existing
pages or `villageService.js`; mock mode and its slug routes remain unchanged.

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
properties are added. Existing cards use demo artwork, so live page integration
will need explicit missing-content handling before it can consume these models.

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
live service wiring, missing-content UI, images and unresolved domain mappings
remain future work. There is no client-side security filtering.

Interactive browser verification was unavailable during adapter validation:
both browser tools failed to connect to the native computer-use pipe. Automated
mock-service tests and HTTP route checks passed, but interactive UI behavior
was not verified in a browser.
