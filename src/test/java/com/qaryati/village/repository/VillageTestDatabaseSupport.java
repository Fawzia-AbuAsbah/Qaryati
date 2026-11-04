package com.qaryati.village.repository;

import com.qaryati.village.entity.Village;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Test-owned, deliberately not Boot auto-configuration. The initializer opens no
 * connection. Every connection from the only DataSource is checked before use.
 * No Flyway or SQL initialization beans exist; Hibernate properties are fixed.
 */
public class VillageTestDatabaseSupport
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String URL = "jdbc:postgresql://localhost:5432/qaryati_test_db";
    static final String APPROVED = "qaryati.village-tests.database-verified";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }

    static void validate(ConfigurableEnvironment environment) {
        require(Arrays.equals(new String[]{"test"}, environment.getActiveProfiles()));
        require(environment.getProperty(APPROVED, Boolean.class, false));
        require(URL.equals(environment.getProperty("spring.datasource.url")));
        require("validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto")));
        require("false".equals(environment.getProperty("spring.flyway.enabled")));
        require("true".equals(environment.getProperty("spring.flyway.clean-disabled")));
        require("never".equals(environment.getProperty("spring.sql.init.mode")));

        // Inspect original enumerable sources too: reject even shadowed routing
        // overrides, including relaxed environment/JVM spelling. Never print values.
        Set<String> datasourceKeys = Set.of("springdatasourceurl", "springdatasourceusername",
                "springdatasourcepassword");
        Set<String> jpaKeys = Set.of("springjpahibernateddlauto", "springjpashowsql",
                "springjpaopeninview");
        Set<String> flywayKeys = Set.of("springflywayenabled", "springflywaycleandisabled");
        for (var propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) continue;
            for (String key : enumerable.getPropertyNames()) {
                String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (normalized.equals("springprofilesactive")) {
                    require("test".equals(enumerable.getProperty(key)));
                }
                if (normalized.startsWith("springdatasource")) {
                    require(datasourceKeys.contains(normalized));
                    if (normalized.equals("springdatasourceurl")) {
                        require(URL.equals(enumerable.getProperty(key)));
                    }
                }
                if (normalized.startsWith("springjpa")) require(jpaKeys.contains(normalized));
                if (normalized.equals("springjpahibernateddlauto")) {
                    require("validate".equals(enumerable.getProperty(key)));
                }
                if (normalized.startsWith("springflyway")) require(flywayKeys.contains(normalized));
                if (normalized.startsWith("hibernate") || normalized.startsWith("jakartapersistence")) {
                    require(false);
                }
                if (normalized.startsWith("springsqlinit") && !normalized.equals("springsqlinitmode")) {
                    require(false);
                }
                // A plain Spring context does not parse Boot JSON/config imports.
                // Reject these instead of implying that their contents were validated.
                if (normalized.equals("springapplicationjson") || normalized.startsWith("springconfig")
                        || normalized.equals("springprofilesinclude")
                        || normalized.startsWith("springprofilesgroup")) require(false);
            }
        }
        require(environment.getProperty("spring.datasource.username") != null);
        require(environment.getProperty("spring.datasource.password") != null);
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException(
                "Village tests require explicitly approved, isolated test configuration; values withheld");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = VillageRepository.class)
    static class PersistenceConfiguration {
        @Bean
        DataSource dataSource(ConfigurableEnvironment environment) {
            // Recheck immediately before constructing the only connection factory.
            validate(environment);
            return new IdentityCheckedDataSource(new DriverManagerDataSource(URL,
                    environment.getRequiredProperty("spring.datasource.username"),
                    environment.getRequiredProperty("spring.datasource.password")),
                    environment.getRequiredProperty("spring.datasource.username"));
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(Village.class.getPackageName());
            var adapter = new HibernateJpaVendorAdapter();
            adapter.setGenerateDdl(false);
            factory.setJpaVendorAdapter(adapter);
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "validate",
                    "hibernate.show_sql", "false"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    static final class IdentityCheckedDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final String expectedUser;

        IdentityCheckedDataSource(DataSource delegate, String expectedUser) {
            this.delegate = delegate;
            this.expectedUser = expectedUser;
        }

        @Override
        public Connection getConnection() throws SQLException {
            try {
                return verify(delegate.getConnection());
            } catch (SQLException failure) {
                throw new SQLException("Test connection unavailable or identity rejected; details withheld");
            }
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            // Alternative per-call credentials are unsupported.
            throw new SQLException("Per-call test datasource credentials are prohibited");
        }

        private Connection verify(Connection connection) throws SQLException {
            try {
                if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                    throw new SQLException("Test database product mismatch");
                }
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery("SELECT current_database(), current_user")) {
                    if (!result.next() || !"qaryati_test_db".equals(result.getString(1))
                            || !expectedUser.equals(result.getString(2))) {
                        throw new SQLException("Test database identity mismatch");
                    }
                }
                return connection;
            } catch (SQLException | RuntimeException failure) {
                try { connection.close(); } catch (SQLException ignored) { }
                // Do not expose driver exceptions, which may contain connection details.
                throw new SQLException("Test connection identity verification failed; details withheld");
            }
        }
    }
}