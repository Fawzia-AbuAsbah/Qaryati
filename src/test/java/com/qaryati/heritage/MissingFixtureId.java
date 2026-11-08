package com.qaryati.heritage;

import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reserves no records: checks a positive missing ID while excluding concurrent table writes. */
public final class MissingFixtureId {
    private MissingFixtureId() {}
    public static long in(JdbcTemplate jdbc, String table) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Missing-ID checks require a surrounding transaction");
        if (!Set.of("sources", "villages", "village_histories", "cultural_heritage").contains(table))
            throw new IllegalArgumentException("Unsupported fixture table");
        // Call only inside the surrounding test transaction; lock is released on rollback.
        jdbc.execute("LOCK TABLE public." + table + " IN SHARE MODE");
        long candidate = Long.MAX_VALUE;
        while (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM public." + table + " WHERE id=?)", Boolean.class, candidate))) {
            if (--candidate == 0) throw new IllegalStateException("No positive missing fixture ID");
        }
        return candidate;
    }
}
