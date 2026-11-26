package com.qaryati.integration;

import com.qaryati.heritage.DedicatedPostgresSafety;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Checks geocoding-test approval and database identity before context refresh. */
public final class GeocodingTestDatabaseGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String APPROVAL_VARIABLE = "QARYATI_GEOCODING_TESTS";
    private final boolean approved;

    public GeocodingTestDatabaseGuard() {
        this(System.getenv(APPROVAL_VARIABLE));
    }

    // Keep offline regressions independent of the process's real approval setting.
    GeocodingTestDatabaseGuard(String approval) {
        approved = "verified-test-db".equals(approval);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        DedicatedPostgresSafety.initialize(context, approved, true);
    }
}
