package com.qaryati.dataquality;

import com.qaryati.heritage.DedicatedPostgresSafety;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Checks data-quality-test approval and database identity before context refresh. */
public final class DataQualityTestDatabaseGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String APPROVAL_VARIABLE = "QARYATI_DATAQUALITY_TESTS";
    private final boolean approved;

    public DataQualityTestDatabaseGuard() {
        this(System.getenv(APPROVAL_VARIABLE));
    }

    // Keep offline regressions independent of the process's real approval setting.
    DataQualityTestDatabaseGuard(String approval) {
        approved = "verified-test-db".equals(approval);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        DedicatedPostgresSafety.initialize(context, approved, true);
    }
}
