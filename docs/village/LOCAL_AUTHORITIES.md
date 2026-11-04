# Local Authority Management

Local Authorities belong to Governorates and use the existing
`public.local_authorities` schema. The Village module provides CRUD, optional
Governorate filtering, request validation, and role authorization. Village CRUD,
VillageLookup, Governorate reassignment, pagination, and sorting are outside this
feature.

## HTTP API

Base path: `/api/v1/local-authorities`. Requests and responses use JSON.

| Method | Path relative to base | Successful result |
| --- | --- | --- |
| POST | collection | 201, created authority |
| GET | collection | 200, array of authorities |
| GET | `?governorateId=2` | 200, array for that Governorate |
| GET | `/{id}` | 200, authority |
| PUT | `/{id}` | 200, replacement authority |
| DELETE | `/{id}` | 204, empty body |

The collection GET uses one handler: absent Governorate filter delegates to
`getAll`; a supplied filter delegates to `getByGovernorate`. An existing
Governorate without authorities returns `[]`; a nonexistent Governorate returns
404. Lists have no guaranteed ordering and are not paginated.

Example POST request, assuming Governorate 2 exists:

```json
{
  "governorateId": 2,
  "nameAr": "مجلس القرية",
  "nameEn": "Village Council",
  "authorityType": "VILLAGE_COUNCIL"
}
```

Example POST, GET-by-ID, or PUT response; IDs are illustrative:

```json
{
  "id": 1,
  "governorateId": 2,
  "nameAr": "مجلس القرية",
  "nameEn": "Village Council",
  "authorityType": "VILLAGE_COUNCIL"
}
```

Collection responses wrap response objects in an array. Request fields do not
include an ID. Supported authority types are `MUNICIPALITY`,
`VILLAGE_COUNCIL`, and `OTHER`, persisted as strings.

## Validation and replacement rules

| Request field | Rule for POST and PUT |
| --- | --- |
| `governorateId` | Required positive Long; Governorate must exist |
| `nameAr` | Required, nonblank, at most 180 characters |
| `nameEn` | Optional/null, at most 180 characters |
| `authorityType` | Required supported enum value |

Names are stored without trimming or normalization. Arabic-name uniqueness is
scoped to the Governorate, enforced by application checks and a PostgreSQL unique
constraint. The same Arabic name may exist in different Governorates. Database
string comparison follows its collation; the API adds no case-folding policy.

PUT replaces all scalar fields. It is not a partial update: required fields must
be supplied. Explicit null or omission of `nameEn` clears the previous English
name. For example:

```json
{
  "governorateId": 2,
  "nameAr": "مجلس القرية",
  "nameEn": null,
  "authorityType": "MUNICIPALITY"
}
```

The authority ID and Governorate relationship are preserved. Updating to a
different existing `governorateId` temporarily returns 409 with
`Changing a local authority's governorate is not supported`. A nonexistent
Governorate returns 404. Uniqueness checks exclude the authority being updated,
so retaining its own Arabic name is allowed.

An authority without references can be deleted. A Village reference prevents
deletion through PostgreSQL foreign keys; the Service translates the integrity
failure to 409. The composite Village/authority foreign key also requires both
records to belong to the same Governorate.

## Authorization and errors

Production Bearer authentication and enabled method security apply.

| Operation | VIEWER | EDITOR | ADMIN |
| --- | --- | --- | --- |
| All GET variants | Allowed | Allowed | Allowed |
| POST and PUT | Forbidden | Allowed | Allowed |
| DELETE | Forbidden | Forbidden | Allowed |

Authenticated users without an allowed role receive 403. Anonymous requests
receive 401. Rejected authorization requests do not invoke the feature Service.
Production CSRF is disabled; authorized writes do not require CSRF tokens.

| Status | Meaning |
| --- | --- |
| 400 | Invalid request fields, unknown enum, malformed JSON, or nonnumeric ID/filter |
| 401 | Missing authentication or invalid bearer token |
| 403 | Authenticated user lacks an allowed role |
| 404 | Requested authority or required Governorate does not exist |
| 409 | Duplicate scoped Arabic name, prohibited Governorate reassignment, or translated database integrity conflict |

The existing global handler formats DTO validation, not-found, and conflict
errors with `timestamp`, `status`, `error`, `message`, and `path`. Malformed JSON
and numeric conversion are resolved by Spring; the MVC tests establish their
400 status, not an identical error-body format. Database integrity conflicts
use general messages and do not expose SQL details.

## Verification results and limits

| Test class | Passing invocations | Evidence |
| --- | ---: | --- |
| LocalAuthorityServiceTest | 23 | Database-free unit tests |
| LocalAuthorityControllerTest | 70 | Database-free MVC slice |
| LocalAuthorityTestDatabaseGuardTest | 58 | Database-free configuration and mocked JDBC checks |
| ModulithArchitectureTest | 1 | Existing database-free module boundary verification |
| LocalAuthorityRepositoryIntegrationTest | 22 | User-reported manual Phase 6F PostgreSQL run |

All reported runs completed with zero failures, errors, or skips: 152
database-free invocations plus 22 repository invocations, 174 across separate
runs. Database-free production/test compilation also passed. No full-suite,
full application, or end-to-end JWT verification is claimed.

Service coverage includes mapping, replacement/optional-name clearing,
uniqueness, missing resources, Governorate reassignment rejection, and integrity
exception translation. MVC coverage includes statuses, response JSON, exact
delegation, all role/handler combinations, anonymous/no-role denial, validation,
malformed input, and existing exception-handler mappings. It imports production
security configuration and the authority converter; JWT decoding, user lookup,
and the feature Service are mocked.

Repository coverage includes identity and all fields, all three enum types,
optional/boundary names, filtering, scoped uniqueness and current-ID exclusion,
duplicate updates, invalid/null/oversized database values, missing Governorate
FK, unreferenced deletion, Village-restricted deletion, composite FK, and lazy
Governorate response mapping inside a transaction.

Phase 6D user inspection verified PostgreSQL 17.4 on localhost:5432,
`qaryati_test_db`, user `postgres`, required constraints, roles, and successful
V1/V2 history. Offline Phase 6E calculation using the installed Flyway 12.4.0
checksum algorithm matched V1 `-977107307` and V2 `428195810`. Only V1/V2 were
found in the repository's default `classpath:db/migration` location. This
checksum comparison is not a complete schema-drift assessment.

## PostgreSQL test safety

Never target `qaryati_db` or `qaryati_governorate_dev`. Future database runs need
explicit approval, independent identity/schema verification, and operational
confirmation that the particular `qaryati_test_db` instance is dedicated to tests.
A database name or approval flag alone does not establish dedication.

The repository tests use a plain Spring persistence context. Their initializer
runs before refresh and checks exactly the test profile, the fixed
`jdbc:postgresql://localhost:5432/qaryati_test_db` URL, explicit verification
flag, Hibernate validate, Flyway disabled/clean disabled, and SQL initialization
disabled. Alternate routing/configuration mechanisms are rejected from
enumerable property sources. The fixed datasource and explicit Hibernate
properties prevent normal environment rebinding; this is not a claim to inspect
every possible custom property source.

No Boot auto-configuration, Flyway execution, Hikari pool, or SQL initializer is
loaded by that test context. Every datasource connection checks PostgreSQL,
database name, and configured user before Hibernate or fixture use. Rejected
connections are withheld and closure is attempted. Credentials must be supplied
securely; never put passwords or JWT secrets into Git, command arguments, or
reports. Do not copy the main application's datasource settings into test runs.

Each invocation creates unique fixtures inside a transaction rolled back at
completion. JdbcTemplate and JPA use the same datasource/transaction manager.
No pre-existing records are changed, no cleanup SQL or commits are used, and
tests issue no further SQL after an expected constraint failure. Identity
sequences can advance despite rollback. The tests require an already migrated
schema and do not create or repair it. Run only explicitly selected test classes;
an unrestricted Maven test command can execute other database tests.