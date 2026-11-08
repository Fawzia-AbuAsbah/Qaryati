package com.qaryati.heritage;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.repository.*;
import com.qaryati.dataquality.service.*;
import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.VillageHistoryRepository;
import com.qaryati.heritage.service.VillageHistoricalOverviewService;
import com.qaryati.village.api.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit opt-in, already migrated dedicated database only. Never run in the database-free selection. */
@EnabledIfEnvironmentVariable(named = "QARYATI_HISTORY_CRUD_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = VillageHistoricalOverviewPostgresIntegrationTest.Config.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = VillageHistoricalOverviewPostgresIntegrationTest.Guard.class)
class VillageHistoricalOverviewPostgresIntegrationTest {
    @Autowired VillageHistoricalOverviewService service;
    @Autowired VillageHistoryRepository repository;
    @Autowired VillageLookupApi villages;
    @Autowired AuditWriter audits;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(audits, villages); }
    private void role(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "reviewer", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    private long[] fixture() {
        long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id", Long.class, UUID.randomUUID().toString());
        long village = jdbc.queryForObject("INSERT INTO public.villages(governorate_id,name_ar,location_description) VALUES (?,'test','test') RETURNING id", Long.class, governorate);
        long source = jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('test','BOOK') RETURNING id", Long.class);
        when(villages.findById(village)).thenReturn(Optional.of(new VillageLookupResult(village, VillageLookupStatus.VERIFIED, false)));
        return new long[]{governorate, village, source};
    }
    private VillageHistoricalOverviewRequest request(long[] fixture, String text) {
        return new VillageHistoricalOverviewRequest(fixture[1], text, List.of(fixture[2]));
    }
    @Test void uniqueConstraintRaceBecomes409() {
        role("ADMIN");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var fixture = fixture();
            service.create(request(fixture, "First"));
            var duplicate = assertThrows(ResponseStatusException.class, () -> service.create(request(fixture, "Second")));
            assertEquals(409, duplicate.getStatusCode().value());
            // Force the authoritative unique constraint path by hiding only the advisory existence check.
            var advisoryBypass = mock(VillageHistoryRepository.class, delegatesTo(repository));
            doReturn(false).when(advisoryBypass).existsByVillageId(fixture[1]);
            try (var validators = Validation.buildDefaultValidatorFactory()) {
                var target = new VillageHistoricalOverviewService(advisoryBypass, villages,
                        new SourceLookupService(sourceRepository), links, audits, validators.getValidator());
                var race = assertThrows(ResponseStatusException.class, () -> target.create(request(fixture, "Racer")));
                assertEquals(409, race.getStatusCode().value());
            }
            tx.setRollbackOnly();
        });
    }
    @Autowired SourceRepository sourceRepository;
    @Autowired VillageHistorySourceLinks links;
    private static <T> org.mockito.stubbing.Answer<T> delegatesTo(Object target) {
        return org.mockito.AdditionalAnswers.delegatesTo(target);
    }
    @Test void filteredPaginationAndSourceOnlyTimestamp() {
        role("EDITOR");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var fixture = fixture();
            var created = service.create(request(fixture, "  Overview\nنص  "));
            long secondSource = jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('second','BOOK') RETURNING id", Long.class);
            var revised = service.update(created.id(), new VillageHistoricalOverviewRequest(fixture[1], created.overview(), List.of(secondSource)));
            assertEquals(created.updatedAt(), revised.updatedAt());
            role("VIEWER");
            assertEquals(0, service.getAll(0, 1, fixture[1]).totalElements());
            jdbc.update("UPDATE public.village_histories SET verification_status='VERIFIED' WHERE id=?", created.id());
            em.clear();
            var page = service.getAll(0, 1, fixture[1]);
            assertEquals(1, page.totalElements());
            assertEquals(List.of(secondSource), page.content().getFirst().sourceIds());
            tx.setRollbackOnly();
        });
    }
    @Test void actualAuditAndSourceFailuresRollBackWholeCreateThroughSpringProxy() {
        role("ADMIN");
        long[][] fixture = {null};
        try {
            fixture[0] = new TransactionTemplate(transactions).execute(tx -> fixture());
            doThrow(new IllegalStateException("audit failed")).when(audits).recordVillageHistory(any(), any(), any(), any());
            assertThrows(IllegalStateException.class, () -> service.create(request(fixture[0], "Failed audit")));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.village_histories WHERE village_id=?", Integer.class, fixture[0][1]));
            reset(audits);
            // Real source lookup fails inside source linking after a history has been flushed.
            assertThrows(NoSuchElementException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var history = repository.saveAndFlush(VillageHistory.draft(new VillageHistoryRequest(fixture[0][1], "Failed source")));
                links.linkNewHistory(history.getId(), List.of(com.qaryati.heritage.MissingFixtureId.in(jdbc, "sources")));
            }));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.village_histories WHERE village_id=?", Integer.class, fixture[0][1]));
        } finally {
            if (fixture[0] != null) new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("DELETE FROM public.village_histories WHERE village_id=?", fixture[0][1]);
                jdbc.update("DELETE FROM public.sources WHERE id=?", fixture[0][2]);
                jdbc.update("DELETE FROM public.villages WHERE id=?", fixture[0][1]);
                jdbc.update("DELETE FROM public.governorates WHERE id=?", fixture[0][0]);
            });
        }
    }
    @Test void auditFailureRestoresUpdatesAndDeletedHistoryWithItsCascadedLinks() {
        role("EDITOR");
        long[] fixture = new TransactionTemplate(transactions).execute(tx -> fixture());
        try {
            var created = service.create(request(fixture, "Original"));
            doThrow(new IllegalStateException("audit failed")).when(audits).recordVillageHistory(any(), any(), any(), any());
            assertThrows(IllegalStateException.class, () -> service.update(created.id(), request(fixture, "Revised")));
            assertEquals("Original", service.getById(created.id()).overview());
            assertEquals(List.of(fixture[2]), links.sourceIds(created.id()));
            assertThrows(IllegalStateException.class, () -> service.delete(created.id()));
            assertEquals("Original", service.getById(created.id()).overview());
            assertEquals(List.of(fixture[2]), links.sourceIds(created.id()));
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("DELETE FROM public.village_histories WHERE village_id=?", fixture[1]);
                jdbc.update("DELETE FROM public.sources WHERE id=?", fixture[2]);
                jdbc.update("DELETE FROM public.villages WHERE id=?", fixture[1]);
                jdbc.update("DELETE FROM public.governorates WHERE id=?", fixture[0]);
            });
        }
    }
    @org.springframework.boot.test.context.TestComponent
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class})
    @org.springframework.transaction.annotation.EnableTransactionManagement
    @EntityScan(basePackageClasses = {VillageHistory.class, Source.class})
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses = {VillageHistoryRepository.class, SourceRepository.class},
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {VillageHistoryRepository.class, SourceRepository.class}))
    @Import({VillageHistoricalOverviewService.class, VillageHistorySourceLinksService.class})
    static class Config {
        @Bean VillageHistorySourceLinkRepository links(EntityManager em) { return new VillageHistorySourceLinkRepository(em); }
        @Bean SourceLookup sources(SourceRepository repository) { return new SourceLookupService(repository); }
        @Bean VillageLookupApi villages() { return mock(VillageLookupApi.class); }
        @Bean AuditWriter audits() { return mock(AuditWriter.class); }
        @Bean(destroyMethod = "close") ValidatorFactory validators() { return Validation.buildDefaultValidatorFactory(); }
        @Bean Validator validator(ValidatorFactory factory) { return factory.getValidator(); }
    }
    static class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            com.qaryati.heritage.DedicatedPostgresSafety.initialize(context,
                    "verified-test-db".equals(System.getenv("QARYATI_HISTORY_CRUD_TESTS")), false);
        }
    }
}
