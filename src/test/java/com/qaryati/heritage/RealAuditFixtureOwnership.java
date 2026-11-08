package com.qaryati.heritage;

import java.util.*;

/** IDs are registered only after INSERT RETURNING succeeds; markers are also checked in SQL. */
final class RealAuditFixtureOwnership {
    private final Map<String, Set<Long>> allocated = new HashMap<>();

    long register(String kind, long id) {
        if (id <= 0) throw new IllegalArgumentException("Fixture ID must be positive");
        allocated.computeIfAbsent(kind, ignored -> new HashSet<>()).add(id);
        return id;
    }

    boolean owns(String kind, long id) {
        return id > 0 && allocated.getOrDefault(kind, Set.of()).contains(id);
    }
}
