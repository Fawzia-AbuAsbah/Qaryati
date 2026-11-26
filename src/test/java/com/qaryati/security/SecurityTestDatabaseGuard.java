package com.qaryati.security;

import com.qaryati.heritage.DedicatedPostgresSafety;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Checks security-test approval and database identity before context refresh. */
public final class SecurityTestDatabaseGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String APPROVAL_VARIABLE = "QARYATI_SECURITY_TESTS";
    private final boolean approved;

    public SecurityTestDatabaseGuard() {
        this(System.getenv(APPROVAL_VARIABLE));
    }

    // Keep offline regressions independent of the process's real approval setting.
    SecurityTestDatabaseGuard(String approval) {
        approved = "verified-test-db".equals(approval);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        DedicatedPostgresSafety.initialize(context, approved, true);
    }
}
