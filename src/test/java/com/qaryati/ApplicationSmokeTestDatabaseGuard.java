package com.qaryati;

import com.qaryati.heritage.DedicatedPostgresSafety;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Checks approval and database identity before application beans can initialize. */
public final class ApplicationSmokeTestDatabaseGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String APPROVAL_VARIABLE = "QARYATI_APPLICATION_SMOKE_TESTS";
    private final boolean approved;

    public ApplicationSmokeTestDatabaseGuard() {
        this(System.getenv(APPROVAL_VARIABLE));
    }

    // Keep offline regressions independent of the process's real approval setting.
    ApplicationSmokeTestDatabaseGuard(String approval) {
        approved = "verified-test-db".equals(approval);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        DedicatedPostgresSafety.initialize(context, approved, true);
    }
}
