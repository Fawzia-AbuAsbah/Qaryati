package com.qaryati.population.repository;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Runs before context refresh, before Flyway or a DataSource can connect. */
public class PopulationTestDatabaseGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final String TEST_URL = "jdbc:postgresql://localhost:5432/qaryati_test_db";
    static final String VERIFIED = "qaryati.population-tests.database-verified";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }

    static void validate(Environment environment) {
        if (!java.util.Arrays.equals(environment.getActiveProfiles(), new String[]{"test"})) {
            throw new IllegalStateException("Population repository tests require only the test profile");
        }
        if (!TEST_URL.equals(environment.getProperty("spring.datasource.url"))) {
            throw new IllegalStateException("Population repository tests require " + TEST_URL);
        }
        for (String property : new String[]{"spring.flyway.url",
                "spring.jpa.properties.jakarta.persistence.jdbc.url", "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.data-source-properties.url"}) {
            String value = environment.getProperty(property);
            if (value != null && !TEST_URL.equals(value)) {
                throw new IllegalStateException("Unsafe test database override: " + property);
            }
        }
        for (String property : new String[]{"spring.datasource.jndi-name",
                "spring.datasource.hikari.data-source-class-name"}) {
            if (environment.getProperty(property) != null) {
                throw new IllegalStateException("Unsupported test datasource override: " + property);
            }
        }
        if (!environment.getProperty(VERIFIED, Boolean.class, false)) {
            throw new IllegalStateException("Verify the dedicated qaryati_test_db before setting " + VERIFIED + "=true");
        }
        if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !environment.getProperty("spring.flyway.clean-disabled", Boolean.class, false)
                || !environment.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Tests require ddl-auto=validate, Flyway enabled and clean disabled");
        }
    }
}
