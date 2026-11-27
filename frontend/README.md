# Qaryati frontend

The React/Vite app defaults to the existing mock data through
`src/services/villageService.js`. Home, Map, Explorer and Details share a mode,
with independent public capabilities described in the Phase 5.6 section below.
Mock Map and Details retain their existing renderers; mock Home keeps
its original featured villages, hero and navigation.
Starting the frontend does not start the backend or connect to PostgreSQL.

## Phase 5.7: Public Home and Map integration

Baseline: `59fcf8c` on `roaa/frontend-final-integration`, initially clean with
943 passing tests. Fetching remote refs found no incoming changes;
`origin/main` remains `4e2d137`. Implementation uses the checked-out public
Village controller/DTO/service/security contracts and integration documentation.
Public catalogues and rich sections remain unavailable. Mock remains the default.

Home keeps its existing hero, navigation and mock featured selection. API Home
search submits only the encoded `q` UI parameter to `/villages`; Explorer maps it
to the backend `search` parameter on `/api/v1/public/villages`. Arabic, English,
reserved characters, empty searches and backend Java whitespace semantics are
covered. Home and Explorer share the public search normalizer and 180 UTF-16-unit
limit; mock normalization remains unchanged. The input supports automatic text
direction and accessible validation feedback. API featured content remains
unavailable, with no request, arbitrary selection or mock substitution. Home
displays no village statistics, so no simulated count is presented as real data.

Map retains the existing Leaflet design and public all-page retrieval service:
GET `/api/v1/public/villages`, ID ascending, default page size 100, maximum 100
pages and 10,000 records. The backend supplies only VERIFIED, non-archived rows.
Every page uses the public DTO adapter and exact pagination validation; duplicate
IDs, order/count changes, malformed data, failed pages and exceeded limits fail
the entire load. Retry starts a fresh traversal. Partial collections never become
complete maps. Offset pagination still provides no database snapshot guarantee.
Public requests use the plain client without Bearer headers or cookies, including
while an account is authenticated. No protected catalogues or rich sections are
requested, and no geographic names or images are inferred from mock data.

The Map render boundary accepts only finite two-element `[latitude, longitude]`
pairs within geographic ranges. Real zero coordinates remain valid. Invalid API
axes fail the adapter; missing pairs retain their villages in the index without
markers. Counts distinguish all public villages from valid markers, including
empty datasets. No-marker datasets never mount Leaflet; one marker gets an
explicit bounded point view; multiple markers fit their actual bounds. Selecting
a known marker centers it, and reset fits all valid locations. Resizing invalidates
Leaflet's cached size through ResizeObserver, with normal Leaflet window-resize
handling available when the observer is unsupported.

The API index provides native buttons for selecting markers and clearing the
selection, preserving unrelated/repeated URL parameters without reloading the
dataset. Numeric Details links remain available independently of marker presence.
Markers and the map retain keyboard handling, Arabic instructions and zoom labels;
the index provides an alternative to popup navigation. Popups use React text and
actual numeric IDs. Reset closes the old selected popup. Long popup/index content
wraps within the existing responsive design, and focus remains visible inside the
map frame. OpenStreetMap copyright attribution remains visible and linked.

Village API loading/failure/retry states are separate from tile loading/failure.
Tile errors keep validated village data and navigation available; their retry
redraws only the tile layer and never reloads Village data. There are no mutation
retries or API-to-mock fallback paths.

Validation: 988 tests pass (45 added), with successful build, lint and whitespace
checks. Coverage includes real Home-to-Explorer service handoff, public transport
isolation across login/logout, multi-page retrieval/limits/errors/cancellation,
stale success/failure, coordinate order/zero/invalid pairs, bounds, selection URLs,
counts and mock regression. Actual Home/Map JSX renders are exercised using
installed React/Vite with only data hooks and DOM-dependent Leaflet bindings
replaced. Those tests check the props reaching map/markers, safe popup content,
Details links, keyboard labels/controls, attribution and loading/empty/error states;
they do not execute Leaflet in a real browser or establish interactive behavior.

Secure browser checks were attempted without disabling security protections.
Headless Edge started but its renderer timed out with sandboxing enabled. The
browser access host was unavailable; native Chrome access timed out waiting for
app approval. Consequently Phase 5.7 has no completed interactive, responsive
viewport or keyboard browser checks. Temporary frontend-only HTTP fixtures were
stopped without contacting a backend or database. The historical 12 Phase 5.6
simulated browser scenarios below were not rerun and are not Phase 5.7 validation.
Live backend/CORS, real tiles, focus/keyboard interaction, resize/RTL/mobile layout
and live dataset/coordinate quality remain unverified. No dependencies, backend,
proxy, default-mode or publication-policy configuration was changed.

Earlier phase reports below describe their historical gates and validation.
Phase 5.6 supersedes the old shared closed gate for basic public browsing/identity.

## Phase 5.5: Village Source Linking wiring

Baseline: `b33e914` on `roaa/frontend-final-integration`. Rechecked source/link
controllers, DTOs, service authorization, repository insertion behavior, exception
handlers, security configuration, MVC/service tests and the merged integration
contract. No backend execution or database access was performed.

Existing protected contracts, relative to `/api/v1`:

| Endpoint | Response / input | Permission |
| --- | --- | --- |
| GET `/villages/{villageId}/sources` | Unpaged `{villageId, sourceId, claimNote}[]` | VIEWER, EDITOR, ADMIN |
| POST `/villages/{villageId}/sources` | `{sourceId, claimNote}` -> 201 association object | EDITOR, ADMIN |
| GET `/sources/{sourceId}/villages` | Unpaged association array | VIEWER, EDITOR, ADMIN |
| GET `/sources` | Unpaged SourceResponse array; no HTTP search/type filters | VIEWER, EDITOR, ADMIN |
| GET `/sources/{id}` | SourceResponse; 404 if missing | VIEWER, EDITOR, ADMIN |

SourceResponse fields remain `id`, `title`, `publisher`, `author`, `sourceType`,
`url`, `publicationDate`, `accessedAt`, `citation`, `createdAt`. Link path IDs and
sourceId must be positive. The frontend additionally requires canonical positive
decimal route strings and JavaScript-safe numeric wire IDs. `claimNote` is optional
nullable text with no backend length limit; whitespace and nonempty notes are
preserved exactly. The form maps an empty note to null. No actor, status or
timestamp is supplied by the browser.

Link creation checks Village/Source existence and returns 404 when either is
missing. It checks duplicates and uses atomic insert-if-absent; 409 never replaces
an existing note. Positive unknown link lookup IDs can return `[]`, without a
parent-existence check. These reads do not establish public Village eligibility.
Data Quality handled errors use `{error:string}` for 400/404/409; security 401/403
and other infrastructure bodies remain defensive API-client cases.

`ApiVillageDetails` now mounts the existing `VillageSourcesPanel` with the loaded
`village` and `villageDetailsService` descriptor. The panel obtains the existing
AuthProvider-scoped Sources service through `useSourcesService`; it never accepts
manual Village IDs or fabricates a village identity. SourcesPage's identity-less
panel mount is removed and replaced with guidance to the real-village context.
The panel first checks matching safe identity and an available API integration,
then current read permission. Anonymous, empty/invalid roles, stale roles, expired
sessions and failed role refreshes initiate no protected source requests.

`villageSourcesState.js` keeps independent link-list, catalogue and mutation
states. It reuses the existing latest-request and single-mutation controllers.
VIEWER gets associations and source titles without a disabled linking form.
EDITOR/ADMIN can select an existing, currently unlinked source and submit an
optional note. Loading/error states and an empty/all-linked catalogue do not
offer an unusable form. Known associations cannot be resubmitted; concurrent
server duplicates remain 409 errors, preserving the draft and existing notes.
Manual list/catalogue refreshes issue GET only. No mutation retries automatically.

A confirmed link remains successful independently of the following list refresh.
If that GET fails, the UI keeps the returned association/note and explicitly says
the link succeeded but list refresh failed. Its retry issues only GET, never POST.
Navigation/unmount cancels reads and writes; old Village/session responses are
discarded. State is redacted when role checks fail or the service becomes stale.
Cancellation cannot undo an operation already committed by the backend.

Reverse source associations show editorial Village IDs and escaped claim notes,
with an explicit notice that an association does not establish public visibility.
They do not link IDs into mock Details or imply a public detail route exists.
No unlink/delete operation, new API, proxy, public endpoint migration, additional
Village section, dependency or backend change is introduced.

**Reachability remains deliberately blocked:** the shipped public Village
capability is still false and the default mode is still mock. Mock Details does
not mount this panel. The real-village wiring is prepared and tested, but users
cannot reach its linking form until real Village Details integration is separately
approved. Sources Management and its permitted reverse-association reads continue
to work independently of that future Village phase, subject to routing/CORS.

Validation: 868 tests passed (79 added), production build, ESLint and
`git diff --check` passed. Actual JSX is rendered with installed React/Vite in an
isolated middleware module graph with no HTTP listener; fixture Village data and
integration descriptors verify actual component mounting/props without changing
the shipped gate. State/service tests use simulated Fetch only and cover role
checks, identities, selection, exact notes, duplicate submissions/conflicts,
refresh failures, cancellation, Village changes, logout and late responses.
These are not interactive browser tests. Browser checks could not run because
the browser access host is unavailable. Live backend behavior remains unverified.
The earlier phase notes below describe their delivery snapshots; this phase
supersedes the earlier deferred linking-wiring limitation only.

## Phase 5.4: effective roles and RBAC

Baseline: `roaa/frontend-final-integration` at `4e2d137`, containing PRs #36 and
#37. Rechecked `LoginController`, `JwtAuthenticationConverterConfig`,
`DatabaseUserDetailsService`, `LoginControllerTest` and
`docs/integration/FRONTEND_BACKEND_CONTRACTS.md`. `/auth/me` returns
`{username, roles}` with exact role names VIEWER, EDITOR and ADMIN. The server
uses current database-backed authorities, removes the ROLE_ prefix, deduplicates
and sorts recognized roles. JWT claims, usernames and browser storage are never
permission sources. Backend authorization remains authoritative on every request.

`authService.js` validates the complete roles array, copies it and freezes both
the array and profile. Multiple roles and `[]` are preserved; no default role is
assigned. Missing, malformed or unknown role data, including an ADMIN mixed with
an unknown role, yields `roles: null, rolesVerified: false`. The authenticated
identity can still use the backend's authentication-only reads, but every
role-dependent operation fails closed. Invalid usernames still reject the profile.

`authRoles.js` enables only the effective-role capability. The existing
`effectiveSourceRolesVerified` export aliases it for the Member 3 services.
The public Village gate remains false and mock browsing remains the default.
No dependencies, backend files, API paths, proxy, village sections or linking
wiring are changed by this phase.

| Operation | Explicit permission |
| --- | --- |
| Sources and source-link reads | VIEWER, EDITOR or ADMIN |
| Source create/update and link creation | EDITOR or ADMIN |
| Verification history and village conflict reads | Any authenticated account, including empty/unverified roles |
| Verification decision | ADMIN |
| Conflict creation and status lists | EDITOR or ADMIN |
| Conflict review/resolve | ADMIN |
| Audit reads | ADMIN |

Generic guards use an OR allowlist with no implicit inheritance: an ADMIN-only
profile does not satisfy a VIEWER-only requirement. `memberRoutes` shares explicit
route policies between App and Navbar. Insufficient roles show a forbidden state;
unverified roles show an unavailable state; anonymous/expired sessions go to Login.
Page/service permission checks remain in place and block insufficient requests.

Roles are a snapshot from login or the existing `refreshProfile` operation.
Navbar's **تحديث الصلاحيات** action explicitly reloads `/auth/me`; there is no
polling or JWT/token refresh. During the check, role-dependent navigation/actions
are suspended, while authentication-only reads remain permitted. Successful
refresh replaces the immutable profile, invalidating old Member 3 services and
their late responses. Invalid role data replaces previous grants with unverified
roles. Logout, expiry, cancellation and generation/stale-response protections are
unchanged. A current-session 401 clears authentication. A 403 or network refresh
failure retains identity and the previous immutable profile with an explicit
error, but marks the snapshot `rolesStale: true`. Route/service checks block all
role-dependent operations until a refresh succeeds; authenticated-only reads
remain permitted. Navbar explicitly labels this stale state. A cancelled retry
cannot restore stale grants. `rolesVerified` describes the profile's role-data
validity, not freshness; `rolesStale` separately tracks failed revalidation and is
cleared only by successful profile verification or session termination. A backend
role change can still produce 403 before the user refreshes the UI. No forbidden
operation is automatically retried. This freshness check was added during the
final pre-commit review, preserving expiration and generation protections.

Service-level Village Source Linking permissions are enabled, but its UI wiring
is deliberately deferred. Verification/conflict village panels still require a
real village identity and the closed Village integration gate. No editorial data
is introduced into mock/public browsing. Live routing/CORS and real backend
validation remain outstanding. The module delivery notes below contain historical
validation totals; Phase 5.4 supersedes their earlier role-contract blockers.

Phase 5.4 tests use simulated Fetch responses only. They exercise all eight role
combinations, malformed role data, route policies, all Member 3 operations,
immutable profiles, refresh/revocation, insufficient-request prevention,
401/403, logout, expiry, session replacement and unchanged mock browsing.
Interactive browser checks could not run: the browser access host is unavailable
and the installed Playwright modules fail to load. No backend, database or
frontend server was started for this phase.

Validation: 789 tests passed (50 added); production build, ESLint and
`git diff --check` passed. These results do not establish live backend or
interactive browser compatibility.

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

## Basic public Village integration (Phase 5.6)

The current behavior in this section supersedes earlier gated integration reports.
Baseline: `c0426d9` on `roaa/frontend-final-integration`. A fetch found no incoming
changes from `origin/main` (`4e2d137`). The checked-out public controller, DTOs,
service/repository eligibility rules, security configuration, tests and
`docs/integration/FRONTEND_BACKEND_CONTRACTS.md` were reviewed. No pending
catalogue, population-filtering or expanded detail contracts are assumed.

`publicVillageCapabilities` in `villageMode.js` separates basic browsing and
identity (enabled) from geographic catalogues and rich sections (disabled).
The shared mode variable retains `mock` as the default. Explicit `api` mode
uses real public endpoints and reports errors without mock fallback. Mode selection
does not approve missing contracts. Factory overrides independently disable browsing
or identity for tests; no environment switch enables catalogues or rich sections.
Keep the default until separately approved live integration validation.

Verified paths, relative to the unchanged `/api/v1` base:

| Request | Contract |
| --- | --- |
| GET `/public/villages` | Paged public DTOs; optional `search`, `governorateId`, `localAuthorityId`, `page`, `size`, `sortBy`, `sortDir` only |
| GET `/public/villages/{id}` | One public DTO; no query parameters; hidden/missing villages both return 404 |

The backend enforces VERIFIED status and non-archived eligibility before list
pagination/counting and in the detail lookup. The browser sends no status filter
and does not implement publication security. Public requests use the plain API
client with no Bearer token and credentials omitted, including during an active
authenticated session. An invalid token on a public request would still be rejected
by backend security, so the authenticated transport is never used here.

`publicVillageAdapters.js` explicitly validates the seven-field public DTO:
`id`, `nameAr`, `nameEn`, `governorateId`, `localAuthorityId`, `latitude`,
`longitude`. Missing/extra fields, invalid types, unsafe/nonpositive IDs and
invalid coordinate axes fail explicitly. Numeric wire IDs remain numeric in
`backendId` and relationship fields; existing canonical decimal route IDs stay
strings. Nullable English names, local-authority IDs and coordinate axes are
preserved. Partial pairs have no marker; real zero coordinates remain valid.
No names, descriptions, verification metadata, images or domain content are
derived from editorial or mock DTOs.

The public page adapter validates and preserves counts, size, total pages,
element count, first/last/next/previous/empty flags and out-of-range state.
Backend page zero becomes UI page one. Explorer keeps its existing cancellation,
stale-response and bounded last-page correction behavior. Search is literal,
trimmed with backend Java whitespace semantics, with a 180 UTF-16-unit limit.
Page sizes are 1–100 (Explorer default 6), with backend offset limits checked
before HTTP. Both positive geographic ID filters are supported and combine on
the backend; unknown positive IDs produce empty results.

Only `id`, `nameAr` and `nameEn` sorting are offered. Obsolete URL sorts
`createdAt`/`updatedAt` normalize to `id` with a visible explanation.
Direct unsupported service options are rejected before HTTP; editorial URL
parameters are never forwarded. Unknown sort values remain validation errors.
There are no mandatory catalogue requests: API mode shows unavailable names and
active numeric URL filters honestly, without fabricated dropdown entries or
ID mappings. Mock mode retains its name-based governorate dropdown.

Basic API Details displays Arabic/English names, geographic IDs and supplied
coordinate axes. Invalid IDs send no HTTP. Identity 404 never mounts children or
tries an editorial endpoint. Population, historical overviews/events, heritage,
culture, agriculture, facilities and economic activities are explicitly unavailable.
The public Details renderer sends no Heritage, Population, Agriculture, Community,
governorate or local-authority request. Existing editorial adapters remain separate.

After a matching safe public identity loads, the existing authenticated Member 3
panels can mount. Sources reads require VIEWER/EDITOR/ADMIN; linking requires
EDITOR/ADMIN. Verification and village-conflict history reads permit every
authenticated account; role-specific actions retain the Phase 5.4 matrix.
Anonymous/expired sessions mount no protected panels. Empty/invalid/stale roles
cannot read Sources. Logout, profile/session replacement and permission revocation
reuse the existing scoped services and state cleanup; late protected responses
cannot restore revoked data. Backend authorization remains authoritative.
No protected metadata or claim notes are part of the public DTO or anonymous view.

Home keeps its mock featured selection. API Home continues to report featured
content unavailable without requests or an invented selection policy.
The existing Map service now reuses the public listing adapter as a compatibility
change; no Map UI/functionality was expanded. Its bounded all-page traversal,
duplicate/order/count checks, nullable-marker behavior and mock renderer remain.
A dedicated Map phase still needs live dataset/limits review, coordinate quality
and marker/popup/tile interaction validation. Offset pagination has no snapshot
token, so count/order checks do not prove a point-in-time snapshot.

There are no proxy, dependency, backend or security changes. Live routing/CORS,
real backend behavior and catalogue/rich-section contracts remain separate work.
The frontend build alone does not establish live integration.

Phase 5.6 validation: 943 frontend tests passed, with successful build, lint and
diff whitespace checks. Tests include actual JSX rendering and simulated HTTP
contracts, independent capabilities, nullable/missing fields, safe IDs, public
token isolation, unsupported queries/sections, errors, pagination correction,
cancellation, stale responses, Member 3 permissions and mock regression.

Twelve headless Edge browser checks passed against isolated frontend servers
with simulated HTTP responses and test-only API/mock mode definitions. They
checked Explorer paths/filters/obsolete sorting, anonymous Details, 404/invalid
IDs, simulated ADMIN panels, role revocation, failed refresh, logout with a late
Sources response, error/no-fallback behavior, featured unavailability and mock
Home/Explorer/Details/Map navigation. There were zero unexpected API requests
and zero public Bearer headers. Edge required a temporary profile and disabled
browser sandbox in this environment. No user browser profile was used; no backend
or database was contacted. Visual/keyboard/RTL review, live CORS/data behavior
and real Map marker/popup/tile validation remain outstanding. Browser harness
files and logs were temporary and are not part of the repository changes.

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

`RequireAuth` supports protected routes with loading, login redirect, forbidden
and unverified-role states, plus safe internal return navigation. Phase 5.4
consumes effective roles from `/auth/me`; see the current policy above. No username
or JWT decoding is used to infer permissions. Frontend guards control UI only;
backend authorization remains authoritative.

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

## Sources management and village source linking

Fetched remote refs on `roaa/frontend-integration` at `93669e7`; there were no
incoming `origin/main` changes. Reviewed `SourceController`,
`VillageSourceController`, their services/entities, source DTOs and enum,
`DataQualityExceptionHandler`, global handlers and the security configuration.
At that delivery snapshot `/auth/me` returned only username; Phase 5.4 now
consumes the merged effective-role contract.

**Every Sources read is role-dependent.** Controller and service rules require
VIEWER/EDITOR/ADMIN for all four GET contracts and EDITOR/ADMIN for mutations.
There is no Sources endpoint explicitly permitting every authenticated user.
`/sources` now requires an explicitly recognized effective role. VIEWER reads;
EDITOR/ADMIN can create/update. Unverified or insufficient permissions send no
Sources requests. Navbar uses the same route policy; Login preserves safe
`/sources?source=ID` returns. Linking UI wiring remains deferred.

`sourceAccess.js` shares the enabled effective-role capability independently of
the closed Village publication gate. There is no environment switch or demo
role/session. Usernames, JWT claims and unrecognized role data cannot enable
access. Automated tests use simulated HTTP and explicit closed-contract fixtures;
they never create users or contact a backend.
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
redirect, safe login return, role-specific navigation/forms, zero requests for
insufficient permissions, profile refresh, keyboard/RTL behavior, logout cleanup
and unchanged mock browsing. Linking UI still needs separately approved wiring.

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
handlers and security configuration. That historical snapshot had a username-only
`/auth/me`; effective roles are now integrated in Phase 5.4.

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
the enabled effective-role contract. Missing roles do not block the authenticated
GET contract; decision creation requires verified ADMIN. No environment override,
inferred JWT claims or synthetic application sessions are added. Tests use
simulated HTTP and explicit service fixtures.

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

Remaining panel blockers are confirmed backend routing/CORS and approved
real-village API integration. These frontend
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
   routing is ready) to check ADMIN-only decisions, duplicate clicks and success
   followed by a failed GET refresh. A history retry must issue only GET; village
   status must remain unchanged.
5. Check 400/401/403/404 and offline responses, then navigate between villages or
   log out with delayed requests. Old history, saved records and errors must not
   appear in the new village/session. Never bypass the application gates to test.

## Data conflicts

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
the session checks used by Verification. `conflictAccess.js` reuses verified
effective roles: authenticated village reads, EDITOR/ADMIN creation and status
lists, and ADMIN review/resolve. Insufficient or unverified permissions send no
role-dependent requests. Tests use simulated HTTP and explicit service fixtures.

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

## Audit logs (read-only)

Reviewed `AuditLogController`, `AuditLogService`, `AuditLogResponse`, the
`AuditLog` entity, `AuditLogRepository`, `AuditAction`, the `audit_logs` schema,
`DataQualityExceptionHandler` and the controller integration tests. The original
delivery had username-only `/auth/me`; Phase 5.4 integrates effective roles.

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
session checks. `auditAccess.js` requires a verified ADMIN through the enabled
effective-role contract. Other accounts send zero audit requests. The protected
`/audit-logs` page uses an ADMIN route guard. It keeps exactly
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
