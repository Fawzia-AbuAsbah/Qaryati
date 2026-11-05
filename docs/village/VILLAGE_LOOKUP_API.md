# Village Lookup API (Feature 7)

## Purpose and public contract

This is an in-process Java API for other Spring Modulith application modules.
Consumers inject `VillageLookupApi` instead of calling a Village REST endpoint or
importing Village persistence types. Existing Village REST APIs and business logic
are unchanged.

The public package is `com.qaryati.village.api`:

```java
public interface VillageLookupApi {
    Optional<VillageLookupResult> findById(Long villageId);
}

public record VillageLookupResult(
        Long villageId,
        VillageLookupStatus status,
        boolean archived
) {}

public enum VillageLookupStatus {
    DRAFT, UNDER_REVIEW, VERIFIED, REJECTED
}
```

The record contains only these three facts. It exposes no JPA entity, repository,
implementation service, `VillageResponse`, or internal `VillageStatus` enum.

## Behavior

| Input or stored state | Result |
| --- | --- |
| Null, zero, or negative ID | `IllegalArgumentException` before any repository access |
| Positive ID with no village | `Optional.empty()` |
| Existing unarchived village | Present result with `archived == false` |
| Existing archived village | Present result with `archived == true` |
| Repository/infrastructure failure | Original exception propagates; no retry or missing-result fallback |

Every status remains visible through this lookup. The API-owned
`VillageLookupStatus` is independent of `com.qaryati.village.entity.VillageStatus`.
The internal implementation uses an exhaustive switch to map each internal value
to its corresponding API value:

| Internal status | API status |
| --- | --- |
| `DRAFT` | `DRAFT` |
| `UNDER_REVIEW` | `UNDER_REVIEW` |
| `VERIFIED` | `VERIFIED` |
| `REJECTED` | `REJECTED` |

Archive state is derived only from `village.getArchivedAt() != null`. The archive
timestamp itself is not part of the public contract.

There is no separate existence method. Callers needing only existence can use
`lookup.findById(id).isPresent()`. Callers needing status/archive state should use
the returned result from a single lookup.

## Spring and Modulith boundary

`village/api/package-info.java` declares `@NamedInterface("lookup")`. The Village
module remains closed by default, with exactly the interface, record, and API enum
exported. Its entity, repository, DTO, mapper, controller, and service packages
remain internal. The qualified named-interface dependency is `village::lookup`
if a consumer later declares an explicit `allowedDependencies` list.

`VillageLookupService` is an internal `@Service` with constructor-injected
`VillageRepository`. Its `findById` method is `@Transactional(readOnly = true)` and
uses only the existing ordinary `VillageRepository.findById` read. Mapping occurs
inside that transaction, using scalar ID/status/archive values. It does not
traverse parent associations, request a pessimistic lock, write, save, flush,
archive, or call HTTP endpoints.

Dependencies remain one-way: Population/Heritage may consume the public Village
API; Village does not depend on Population or Heritage.

## Example consumer

This illustrates constructor injection only; it does not activate any Member 2
service or authorize an operation:

```java
import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.village.api.VillageLookupResult;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class VillageReferenceReader {
    private final VillageLookupApi villages;

    public VillageReferenceReader(VillageLookupApi villages) {
        this.villages = villages;
    }

    public Optional<VillageLookupResult> inspect(Long villageId) {
        return villages.findById(villageId);
    }
}
```

A present result provides `villageId()`, `status()`, and `archived()`. Consumers
choose their own approved missing-village handling and operation policies.

## Eligibility and concurrency limitations

This lookup exposes factual state only. It provides no Population or Heritage
eligibility method and does not assume that only `VERIFIED` villages are eligible.
Member 2 and the team must separately approve how village status and archive state
affect creation and updates. Those rules are distinct from the verification status
and mutation rules of a Population or Heritage record itself.

The result is an immutable state snapshot. It does not guarantee that village
state remains unchanged before a later write or until a consumer transaction
commits. No pessimistic lock or version token is acquired. Default `REQUIRED`
transaction propagation joins an existing consumer transaction; the annotation
does not convert a surrounding write transaction into a read-only transaction.
Any stronger eligibility/concurrency guarantee needs a separately agreed contract.

`HeritageArchitectureTest.heritageProductionCodeHasNoOtherModuleDependencies()`
currently allows only Heritage, Java, Jakarta, and `org` dependencies. Member 2
must narrowly update that test to permit the approved Village API when integrating
it. This feature does not change that test or any Population/Heritage files.
Other pending authorization, visibility, source, and audit contracts also remain
Member 2 integration requirements.

## Offline verification

Executed on 2026-11-05 with Java 21.0.12.1 and locally cached Maven 3.9.11 in
offline mode. Final `BUILD SUCCESS` at `2026-11-05T09:52:58+02:00`:

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `VillageLookupServiceTest` | 14 | 0 | 0 | 0 |
| `VillageLookupArchitectureTest` | 5 | 0 | 0 | 0 |
| Existing `ModulithArchitectureTest` | 1 | 0 | 0 | 0 |
| Existing `HeritageArchitectureTest` | 2 | 0 | 0 | 0 |
| **Total** | **22** | **0** | **0** | **0** |

All 221 production sources and 85 test sources compiled for Java 21. Compilation
included existing integration test sources, but none of those integration tests
ran. Only the four explicitly selected classes executed.

The 14 lookup test invocations cover all four status values with both archive
states, a missing positive ID, null/zero/negative IDs with no repository access,
untranslated infrastructure failures, and Spring injection by `VillageLookupApi`.
They assert that only the ordinary repository read and three entity scalar getters
are used. The Spring test scans the actual service and exercises transaction
interception with a mocked repository and an in-memory transaction manager.

The five Village architecture tests verify the closed module and exact three-type
`lookup` export, the public generic/record/enum contract, no API dependency on
Village internals, no external production access to Village internals, and no
Village dependency on Population or Heritage. Existing Modulith checks also pass.
Heritage's current restriction passes because this feature adds no Heritage
consumer; that restriction still needs Member 2's separate update before use.

Exact successful command (PowerShell):

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot'
& 'C:/Users/youse/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin/mvn.cmd' -o `
    '-Dmaven.repo.local=C:/Users/youse/.m2/repository' `
    '-Dtest=com.qaryati.village.service.VillageLookupServiceTest,com.qaryati.village.VillageLookupArchitectureTest,com.qaryati.ModulithArchitectureTest,com.qaryati.heritage.HeritageArchitectureTest' test
```

An initial sandboxed invocation stopped before compilation/tests because Maven
resolved its local repository to inaccessible `C:\.m2\repository`. The successful
invocation above used the explicit existing cache with approved execution access.
No test failure occurred. Nonfailure notices concerned annotation processing,
existing unchecked test mocks, and Mockito dynamic agent attachment.

No database connection, SQL execution, PostgreSQL integration test, Flyway,
migration, Spring Boot application/server startup, or unrestricted test suite ran.
The lightweight Spring test context has no datasource, persistence context, or
Boot autoconfiguration. This verification establishes Java behavior and module
boundaries; it does not establish live database behavior or concurrent-write
guarantees.
