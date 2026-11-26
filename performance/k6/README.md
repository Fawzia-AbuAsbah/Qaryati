# Guarded Village read workload

**Implemented, not executed. No performance environment or load run is approved.**

This script performs only authenticated Village GETs. It never logs in, creates
fixtures, changes data, starts services, runs migrations or cleans up records.
No measured results are supplied here.

## Isolation is an operator responsibility

Before any future k6 command, independently verify the deployed API instance and
its **actual datasource**, including database/server identity, ownership and
schema readiness. Require an exclusively reserved database containing synthetic
data only, dedicated test accounts, and a stable fixture manifest. Never use
`qaryati_db` or `qaryati_governorate_dev`.

`qaryati_test_db` is **UNVERIFIED**. Its name, the existing Java test guards,
localhost, a port number, a URL match or the attestation string below does not
prove isolation. The script can reject a declared forbidden database name but
cannot establish whether that declaration matches the backend. Obtain separate
execution approval after independent verification; setting environment variables
does not itself authorize a run.

The checked-in Docker Compose stack has persistent storage and startup Flyway
migrations. It is **not an approved performance environment**. Do not launch or
reuse it as part of this script. A separately approved deployment must have its
own database/storage and nonconflicting ports. No application configuration or
database safety guards are changed by this deliverable.

## Source-confirmed API contract

| Request | Required role | Expected response |
| --- | --- | --- |
| `GET /api/v1/villages` | VIEWER, EDITOR or ADMIN | 200, explicit page object |
| `GET /api/v1/villages/{id}` | VIEWER, EDITOR or ADMIN | 200, known Village DTO |

Sources, relative to the repository root:

- `src/main/java/com/qaryati/village/controller/VillageController.java`: list and detail handlers.
- `src/main/java/com/qaryati/village/service/VillageService.java`: pagination, filtering, literal search and archive behavior.
- `src/main/java/com/qaryati/village/dto/VillagePageResponse.java`: eleven page fields.
- `src/main/java/com/qaryati/village/dto/VillageResponse.java`: Village fields and nullable values.
- `src/test/java/com/qaryati/village/controller/VillageControllerTest.java`: response, role and parameter contracts.

Server query parameters are `page` (default 0), `size` (default 20, range 1-100),
`search` (at most 180 UTF-16 code units after stripping), positive optional
`governorateId` and `localAuthorityId`, optional exact `status`, `sortBy` and
`sortDir`. Valid statuses are DRAFT, UNDER_REVIEW, VERIFIED and REJECTED. Server
sort fields are id, nameAr, nameEn, createdAt and updatedAt; direction is asc/desc.
The server also bounds the page offset to a signed 32-bit integer.

This script deliberately pins `sortBy=id&sortDir=asc`; its own page cap is 1000.
Filters combine with AND. Collection/search results exclude archived rows; detail
can legitimately include an archived fixture. Search is a literal substring over
Arabic names or case-insensitive English names, with `%`, `_` and `!` escaped by
the service. The script URL-encodes terms; it does not attempt to reproduce
PostgreSQL collation or Unicode case matching in JavaScript.

Page fields are `content`, `number`, `size`, `totalElements`, `totalPages`,
`numberOfElements`, `first`, `last`, `hasNext`, `hasPrevious`, `empty`. The checks
validate their types/relationships, active content, explicit filters and strictly
ascending IDs. Village checks include identity, parent ID, names/location, status,
timestamps and optional scalar types. Nullable fields may be absent or null.
Empty out-of-range pages are valid even when totalElements is positive. No
cross-request snapshot or increasing-timestamp assumption is made.

## Mandatory configuration

All values come from the runtime environment. There is no default target or
token. Supply secrets privately through the approved local mechanism; never put
them in source, command-line arguments, screenshots, logs or result artifacts.

| Variable | Requirement |
| --- | --- |
| `QARYATI_PERF_BASE_URL` | Exact origin only: lowercase scheme/host, optional explicit port, no trailing slash, path, userinfo, query, fragment or escaping. |
| `QARYATI_PERF_EXPECTED_BASE_URL` | Exact same origin, copied from the independently verified target record. |
| `QARYATI_PERF_APPROVAL` | Literal `verified-exclusive-synthetic-target`, supplied only after independent target verification and separate execution approval. |
| `QARYATI_PERF_DATABASE_NAME` | Operator-attested actual database name: lowercase letters/digits/underscores, first character a letter, at most 63 characters. Forbidden development names are rejected. This is not a backend identity check. |
| `QARYATI_PERF_DATASET_ID` | Nonsecret fixture-manifest identifier, 1-64 letters/digits/underscores/hyphens, beginning with a letter/digit. |
| `QARYATI_PERF_TOKEN` | Raw application Bearer JWT, without a `Bearer ` prefix. Never printed or returned from setup. |
| `QARYATI_PERF_VILLAGE_IDS` | JSON array of 1-10 unique, known, positive synthetic Village IDs, each a canonical decimal string. Numeric JSON values are rejected to prevent rounding before validation. |
| `QARYATI_PERF_SEARCH_TERMS` | JSON array of 1-5 unique synthetic search strings: nonempty, already trimmed, at most 180 code units, no control characters, valid URI encoding. Each must match fixtures with the configured filters. |

HTTPS origins support ASCII DNS names and canonical IPv4 addresses. The only
supported IPv6 literal is `[::1]`. HTTP is restricted to exact localhost,
127.0.0.1 or `[::1]`, and additionally requires
`QARYATI_PERF_ALLOW_HTTP_LOOPBACK=true`. This opt-in does not approve the current
local application or establish database isolation. Remote plaintext HTTP,
single-label remote hosts, ambiguous numeric addresses and credential-bearing
URLs are rejected.

All IDs and response numeric counts must fit JavaScript's safe integer range.
IDs above 9007199254740991 are rejected rather than rounded, although the Java
API supports larger Long values. Fixture/search examples must come from a
reviewed synthetic manifest; the script never guesses IDs or discovers users.

JWT payload decoding checks an integer future `exp`, optional `nbf`, and a
lifetime budget covering setup (120 seconds), the configured run, five seconds
of graceful completion, one request timeout and 30 seconds of margin. It does
**not** verify signatures, issuer or account permissions. The server remains
authoritative; setup verifies access through Village GETs, and each request
rechecks the local expiry margin. No login or token refresh occurs in the loop.
The login endpoint allows five attempts per socket IP per minute; its limiter
must remain unchanged.

## Bounded workload proposals

| Optional variable | Default | Accepted range |
| --- | --- | --- |
| `QARYATI_PERF_VUS` | 1 | 1-20 |
| `QARYATI_PERF_DURATION_SECONDS` | 30 | 10-300 |
| `QARYATI_PERF_PACE_MS` | 1000 | 250-5000 between iterations |
| `QARYATI_PERF_TIMEOUT_SECONDS` | 5 | 1-10 per request |
| `QARYATI_PERF_PAGE` | 0 | 0-1000 |
| `QARYATI_PERF_PAGE_SIZE` | 20 | 1-100 |
| `QARYATI_PERF_GOVERNORATE_ID` | Absent | Positive safe integer |
| `QARYATI_PERF_LOCAL_AUTHORITY_ID` | Absent | Positive safe integer |
| `QARYATI_PERF_STATUS` | Absent | One exact server status |

Defaults are a **proposed future smoke profile**, not an authorization or measured
capacity. Higher VUs/durations are deliberate, separately reviewed operator
choices within these limits. Use only the documented environment parameters,
not scenario/CLI overrides. The script checks consolidated k6 scenario options
before requests and rejects an unexpected executor, VU count, duration, start,
grace period, scenario environment, TLS bypass or HTTP debugging. Keep all k6
outputs local and approved; operator/runtime configuration remains outside the
script's trust boundary. Do not use HTTP debug, cloud output, environment dumps
or insecure TLS options.

Each measured iteration makes one GET. A deterministic ten-slot cycle contains
five listings, three searches and two details (50/30/20); terms/IDs rotate per
cycle using the global scenario iteration counter. Short, interrupted or
in-flight runs can have a different observed mix; report actual per-route counts.
This is a closed-loop constant-VU workload with pacing, not a fixed arrival rate
or an asserted requests-per-second capacity.

Setup validates configuration before any HTTP, then checks page-zero listing,
every search term and every fixture ID with the supplied token. Listing and each
search must return nonempty page-zero content. Setup makes at most 16 GETs and has a
120-second deadline. It returns only a nonsecret completion flag. Measured pages
may be empty if a deliberately chosen page is out of range; choose fixture
cardinality/selectivity deliberately and keep it constant for comparisons.

All requests have redirects disabled, a bounded timeout and JSON response checks.
The body-size check bounds accepted/parsing input after download; it is not a
transport-level download limit.
Only the two Village routes at the configured origin are constructed. No login,
geocoding, external provider, data preparation, mutation or cleanup is included.

## Checks, metrics and stop conditions

Stable tags are `endpoint=list|search|detail`, `phase=setup|load` and a templated
request `name`. URLs/IDs/search strings are not system metric tags. Never log
response objects, bodies, headers or tokens.

Built-in metrics include `http_req_duration` (average/p95), `http_reqs`,
`http_req_failed`, `checks` and iteration metrics. HTTP/check metrics include setup
traffic; filter those by `phase=load` for business results. Iteration metrics also
include pacing and are not request-latency metrics. Expected HTTP status is
explicitly 200, so redirects and other unexpected statuses count as HTTP failures.

Three small custom metrics exclude setup:

- `village_read_duration`: HTTP request duration in milliseconds for returned responses, including failed responses.
- `village_read_requests`: measured request-attempt count and k6-reported rate.
- `village_read_errors`: one sample per returned response or caught client exception, failed for unexpected status or response checks. This catches malformed HTTP-200 responses that HTTP status metrics alone miss.

Attempts are counted immediately before GET. In-flight attempts interrupted by
abort or the grace deadline may have no custom duration/error sample; report
interruption separately and do not treat an aborted run as a baseline.

Provisional correctness thresholds are failed HTTP/request rates below 1%,
successful measured checks above 99%, and at least one measured request. These
are proposals, not agreed production SLOs. No latency or throughput acceptance
target is fabricated. Averages/p95 describe latency; request counts per measured
second describe throughput; failed checks and unexpected HTTP errors are separate
signals. Use the measured-window timestamps when calculating steady-state RPS;
document the denominator used by the installed k6 summary and report setup apart.

The script records then aborts the workload on unexpected status, malformed or
incomplete JSON, wrong response URL, invalid page/DTO semantics, 401/403, missing
fixture/route (404), rate limiting (429), transport failure or application error.
It performs no automatic retries or authentication recovery. An aborted run is
diagnostic evidence, not a valid capacity baseline. Already in-flight requests
may be interrupted when k6 aborts or the grace period ends.

## Future approved execution and reproducibility

**Do not run k6 now.** Before any future execution:

1. Obtain separate approval and record independent deployment/database verification,
   exclusive ownership, synthetic dataset, dedicated account role and token expiry.
2. Verify the installed k6 version/API compatibility. No k6 binary, JavaScript
   execution, syntax checker or load target has been invoked for this change.
   The script uses `k6/execution` (`test.options`, `test.abort`,
   `scenario.iterationInTest`), base64url decoding, custom metrics and HTTP
   `expectedStatuses`. Unsupported options metadata fails before target traffic;
   compatibility and request/response URL normalization still require verification
   with the chosen version. There are no network imports or URL polyfills.
3. Independently prepare and freeze the synthetic fixture manifest outside k6.
   Include account cardinality, active/archive states, parent/status distribution,
   matched search cardinalities and the approved page sizes/depths. A shared token
   models one hot account; representative multi-account traffic is a later profile.
4. Record Git commit, script revision, UTC run ID/timestamps, k6/JDK/database
   versions, dataset ID/counts, effective nonsecret workload settings, deployment
   CPU/memory/pool allocation, cache/warm-up policy and other concurrent load.
   Record no credential values or JWT payloads. Parameter/body validation cost
   also consumes load-generator CPU; check that the generator is not saturated.
5. Use approved local k6 output to capture the tagged metric stream and terminal
   summary. The existing ignored `target/` directory can hold future artifacts
   under `target/k6/`; create it only in a separately authorized execution step.
   Per-route duration/throughput/error analysis comes from tagged samples, not
   invented aggregate subtraction. Artifact files must be reviewed before sharing.
6. Later repeat the same fixed dataset/profile/resources/warm-up for baseline and
   optimization commits. Collect runtime evidence to identify a bottleneck before
   changing code/indexes, and repeat runs to report variation. No bottleneck has
   been measured by this deliverable.

## Results template

**NOT MEASURED: no k6, application or database execution has been authorized.**

| Profile / endpoint | Baseline commit | Requests | Avg ms | p95 ms | Business req/s | HTTP error % | Failed-check % |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Read / listing | NOT MEASURED | | | | | | |
| Read / search | NOT MEASURED | | | | | | |
| Read / detail | NOT MEASURED | | | | | | |

| Profile / endpoint | Optimization commit | Requests | Avg ms | p95 ms | Business req/s | HTTP error % | Failed-check % |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Read / listing | NOT MEASURED | | | | | | |
| Read / search | NOT MEASURED | | | | | | |
| Read / detail | NOT MEASURED | | | | | | |

Measured bottleneck: **NOT MEASURED**. Optimization/change: **NOT IMPLEMENTED**.
Runtime/query evidence, repeat-run variation and before/after interpretation:
**OUTSTANDING**.

The professor's separate authenticated write-oriented scenario, owned-record
cleanup/reconciliation procedure and actual before/after measurements remain
outstanding. This read-only script does not satisfy those deliverables by itself.
