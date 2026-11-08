package com.qaryati.heritage;

import com.qaryati.heritage.entity.VillageHistory;
import com.qaryati.heritage.repository.*;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Explicit primary test configuration, excluded from Boot's application component scans. */
@TestComponent
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class})
@EnableTransactionManagement
@EntityScan(basePackageClasses = VillageHistory.class)
@EnableJpaRepositories(basePackageClasses = VillageHistoryRepository.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {VillageHistoryRepository.class, CulturalHeritageRepository.class}))
class OverviewHeritageRepositoryTestConfig {
    static class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            DedicatedPostgresSafety.initialize(context,
                    "true".equals(System.getProperty("qaryati.heritage-tests.database-verified")), true);
        }
    }
}
