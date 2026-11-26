package com.qaryati.agriculture;

import com.qaryati.heritage.DedicatedPostgresSafety;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Checks agriculture-test approval and database identity before context refresh. */
public final class AgricultureTestDatabaseGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String APPROVAL_VARIABLE = "QARYATI_AGRICULTURE_TESTS";
    private final boolean approved;

    public AgricultureTestDatabaseGuard() {
        this(System.getenv(APPROVAL_VARIABLE));
    }

    // Keep offline regressions independent of the process's real approval setting.
    AgricultureTestDatabaseGuard(String approval) {
        approved = "verified-test-db".equals(approval);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        DedicatedPostgresSafety.initialize(context, approved, true);
    }
}
