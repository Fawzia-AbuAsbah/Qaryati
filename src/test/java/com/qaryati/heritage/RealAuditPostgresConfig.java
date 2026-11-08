package com.qaryati.heritage;

import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.repository.*;
import com.qaryati.dataquality.service.*;
import com.qaryati.heritage.entity.VillageHistory;
import com.qaryati.heritage.repository.VillageHistoryRepository;
import com.qaryati.heritage.repository.CulturalHeritageRepository;
import com.qaryati.heritage.service.*;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.UserLookupService;
import com.qaryati.village.api.VillageLookupApi;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// A primary configuration: @TestConfiguration would make Boot also discover QaryatiApplication.
// TestComponent keeps this explicitly selected configuration out of other Boot component scans.
@Configuration(proxyBeanMethods = false)
@TestComponent
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class})
@EnableTransactionManagement
@EntityScan(basePackageClasses = {VillageHistory.class, Source.class, User.class})
@EnableJpaRepositories(
        basePackageClasses = {VillageHistoryRepository.class, SourceRepository.class, UserRepository.class},
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                VillageHistoryRepository.class, CulturalHeritageRepository.class,
                SourceRepository.class, AuditLogRepository.class, UserRepository.class}))
@Import({VillageHistoricalOverviewService.class, CulturalHeritageCrudService.class,
        VillageHistorySourceLinksService.class, CulturalHeritageSourceLinksService.class,
        SourceLookupService.class, AuditWriterService.class, AuditLogService.class, UserLookupService.class})
class RealAuditPostgresConfig {
    // Only substitution: Member 1's public verification API. Village foreign-key rows remain real.
    @Bean VillageLookupApi villages() { return org.mockito.Mockito.mock(VillageLookupApi.class); }
    @Bean VillageHistorySourceLinkRepository histories(EntityManager em) { return new VillageHistorySourceLinkRepository(em); }
    @Bean CulturalHeritageSourceLinkRepository cultures(EntityManager em) { return new CulturalHeritageSourceLinkRepository(em); }
    @Bean(destroyMethod = "close") ValidatorFactory validators() { return Validation.buildDefaultValidatorFactory(); }
    @Bean Validator validator(ValidatorFactory factory) { return factory.getValidator(); }
}
