package com.qaryati.heritage;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.repository.*;
import com.qaryati.dataquality.service.*;
import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.CulturalHeritageRepository;
import com.qaryati.heritage.service.CulturalHeritageCrudService;
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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
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
@EnabledIfEnvironmentVariable(named = "QARYATI_CULTURAL_CRUD_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = CulturalHeritageCrudPostgresIntegrationTest.Config.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = CulturalHeritageCrudPostgresIntegrationTest.Guard.class)
class CulturalHeritageCrudPostgresIntegrationTest {
    @Autowired CulturalHeritageCrudService service;
    @Autowired CulturalHeritageRepository repository;
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
    private CulturalHeritageCrudRequest request(long[] fixture, String text) {
        return new CulturalHeritageCrudRequest(fixture[1], text, CulturalHeritageType.CRAFT, "Description", List.of(fixture[2]));
    }
    @Test void uniqueConstraintRaceBecomes409() {
        role("ADMIN");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var fixture = fixture();
            service.create(request(fixture, "First"));
            var duplicate = assertThrows(ResponseStatusException.class, () -> service.create(request(fixture, "First")));
            assertEquals(409, duplicate.getStatusCode().value());
            // Force the authoritative unique constraint path by hiding only the advisory existence check.
            var advisoryBypass = mock(CulturalHeritageRepository.class, delegatesTo(repository));
            doReturn(false).when(advisoryBypass).existsByVillageIdAndNameAndHeritageType(fixture[1], "First", CulturalHeritageType.CRAFT);
            try (var validators = Validation.buildDefaultValidatorFactory()) {
                var target = new CulturalHeritageCrudService(advisoryBypass, villages,
                        new SourceLookupService(sourceRepository), links, audits, validators.getValidator());
                var race = assertThrows(ResponseStatusException.class, () -> target.create(request(fixture, "First")));
                assertEquals(409, race.getStatusCode().value());
            }
            tx.setRollbackOnly();
        });
    }
    @Autowired SourceRepository sourceRepository;
    @Autowired CulturalHeritageSourceLinks links;
    @Test void updateUniqueConstraintRaceBecomes409() {
        role("ADMIN");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var fixture=fixture();
            service.create(request(fixture,"First"));
            var second=service.create(request(fixture,"Second"));
            assertEquals(409,assertThrows(ResponseStatusException.class,
                    ()->service.update(second.id(),request(fixture,"First"))).getStatusCode().value());
            var bypass=mock(CulturalHeritageRepository.class,delegatesTo(repository));
            doReturn(false).when(bypass).existsByVillageIdAndNameAndHeritageTypeAndIdNot(
                    fixture[1],"First",CulturalHeritageType.CRAFT,second.id());
            try(var validators=Validation.buildDefaultValidatorFactory()) {
                var target=new CulturalHeritageCrudService(bypass,villages,
                        new SourceLookupService(sourceRepository),links,audits,validators.getValidator());
                assertEquals(409,assertThrows(ResponseStatusException.class,
                        ()->target.update(second.id(),request(fixture,"First"))).getStatusCode().value());
            }
            tx.setRollbackOnly();
        });
    }

    @Test @Timeout(20)
    void concurrentUpdatesSerializeBeforeSnapshotsThroughServiceLocks() throws Exception {
        role("ADMIN");
        long[] fixture=new TransactionTemplate(transactions).execute(tx->fixture());
        var releaseFirst=new java.util.concurrent.CountDownLatch(1);
        var firstLocked=new java.util.concurrent.CountDownLatch(1);
        var secondStarted=new java.util.concurrent.CountDownLatch(1);
        var snapshots=new java.util.concurrent.CopyOnWriteArrayList<CulturalHeritageAuditSnapshot>();
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var created=service.create(request(fixture,"Original"));
            doAnswer(call->{
                CulturalHeritageAuditSnapshot before=call.getArgument(2);
                CulturalHeritageAuditSnapshot after=call.getArgument(3);
                snapshots.add(before);
                if(after.name().equals("First")) {
                    firstLocked.countDown();
                    assertTrue(releaseFirst.await(5,java.util.concurrent.TimeUnit.SECONDS));
                }
                return null;
            }).when(audits).recordCulturalHeritage(eq(created.id()),eq(AuditAction.UPDATE),any(),any());
            var first=workers.submit(()->{
                role("EDITOR");
                try { return service.update(created.id(),request(fixture,"First")); }
                finally { SecurityContextHolder.clearContext(); }
            });
            try {
                assertTrue(firstLocked.await(5,java.util.concurrent.TimeUnit.SECONDS));
                var second=workers.submit(()->{
                    role("EDITOR"); secondStarted.countDown();
                    try { return service.update(created.id(),request(fixture,"Second")); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                assertTrue(secondStarted.await(5,java.util.concurrent.TimeUnit.SECONDS));
                releaseFirst.countDown();
                first.get(5,java.util.concurrent.TimeUnit.SECONDS);
                second.get(5,java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(List.of("Original","First"),snapshots.stream().map(CulturalHeritageAuditSnapshot::name).toList());
                assertEquals("Second",service.getById(created.id()).name());
                assertEquals(List.of(fixture[2]),links.sourceIds(created.id()));
            } finally { releaseFirst.countDown(); }
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                jdbc.update("DELETE FROM public.cultural_heritage WHERE village_id=?",fixture[1]);
                jdbc.update("DELETE FROM public.sources WHERE id=?",fixture[2]);
                jdbc.update("DELETE FROM public.villages WHERE id=?",fixture[1]);
                jdbc.update("DELETE FROM public.governorates WHERE id=?",fixture[0]);
            });
        }
    }
    private static <T> org.mockito.stubbing.Answer<T> delegatesTo(Object target) {
        return org.mockito.AdditionalAnswers.delegatesTo(target);
    }
    @Test void filteredPaginationAndSourceOnlyUpdate() {
        role("EDITOR");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var fixture = fixture();
            var created = service.create(request(fixture, "  Overview\nنص  "));
            long secondSource = jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('second','BOOK') RETURNING id", Long.class);
            var revised = service.update(created.id(), new CulturalHeritageCrudRequest(fixture[1], created.name(), created.heritageType(), created.description(), List.of(secondSource)));
            assertEquals(created.name(), revised.name());
            service.create(new CulturalHeritageCrudRequest(fixture[1],created.name(),CulturalHeritageType.FOOD,"",List.of(secondSource)));
            assertEquals(2, service.getAll(0,1,fixture[1],null,null).totalElements());
            assertEquals(2, service.getAll(0,1,fixture[1],null,null).totalPages());
            role("VIEWER");
            assertEquals(0, service.getAll(0, 1, fixture[1], CulturalHeritageType.CRAFT, null).totalElements());
            jdbc.update("UPDATE public.cultural_heritage SET verification_status='VERIFIED' WHERE id=?", created.id());
            em.clear();
            var page = service.getAll(0, 1, fixture[1], CulturalHeritageType.CRAFT, null);
            assertEquals(1, page.totalElements());
            assertEquals(List.of(secondSource), page.content().getFirst().sourceIds());
            assertEquals(0,service.getAll(0,1,fixture[1],null,VerificationStatus.DRAFT).totalElements());
            assertEquals(0,service.getAll(0,1,fixture[1],CulturalHeritageType.FOOD,null).totalElements());
            tx.setRollbackOnly();
        });
    }
    @Test void actualAuditAndSourceFailuresRollBackWholeCreateThroughSpringProxy() {
        role("ADMIN");
        long[][] fixture = {null};
        try {
            fixture[0] = new TransactionTemplate(transactions).execute(tx -> fixture());
            doThrow(new IllegalStateException("audit failed")).when(audits).recordCulturalHeritage(any(), any(), any(), any());
            assertThrows(IllegalStateException.class, () -> service.create(request(fixture[0], "Failed audit")));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.cultural_heritage WHERE village_id=?", Integer.class, fixture[0][1]));
            reset(audits);
            // Real source lookup fails inside source linking after a history has been flushed.
            assertThrows(NoSuchElementException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var history = repository.saveAndFlush(CulturalHeritage.draft(new CulturalHeritageRequest(fixture[0][1], "Failed source", CulturalHeritageType.CRAFT, "Description")));
                links.linkNewCulturalHeritage(history.getId(), List.of(Long.MAX_VALUE));
            }));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.cultural_heritage WHERE village_id=?", Integer.class, fixture[0][1]));
        } finally {
            if (fixture[0] != null) new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("DELETE FROM public.cultural_heritage WHERE village_id=?", fixture[0][1]);
                jdbc.update("DELETE FROM public.sources WHERE id=?", fixture[0][2]);
                jdbc.update("DELETE FROM public.villages WHERE id=?", fixture[0][1]);
                jdbc.update("DELETE FROM public.governorates WHERE id=?", fixture[0][0]);
            });
        }
    }
    @Test void auditFailureRestoresUpdatesAndDeletedHistoryWithItsCascadedLinks() {
        role("EDITOR");
        long[] fixture = new TransactionTemplate(transactions).execute(tx -> fixture());
        long replacementSource = jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('replacement','BOOK') RETURNING id",Long.class);
        try {
            var created = service.create(request(fixture, "Original"));
            doThrow(new IllegalStateException("audit failed")).when(audits).recordCulturalHeritage(any(), any(), any(), any());
            assertThrows(IllegalStateException.class, () -> service.update(created.id(),
                    new CulturalHeritageCrudRequest(fixture[1],"Revised",CulturalHeritageType.FOOD,"changed",List.of(replacementSource))));
            assertEquals("Original", service.getById(created.id()).name());
            assertEquals(List.of(fixture[2]), links.sourceIds(created.id()));
            assertThrows(IllegalStateException.class, () -> service.delete(created.id()));
            assertEquals("Original", service.getById(created.id()).name());
            assertEquals(List.of(fixture[2]), links.sourceIds(created.id()));
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("DELETE FROM public.cultural_heritage WHERE village_id=?", fixture[1]);
                jdbc.update("DELETE FROM public.sources WHERE id=?", fixture[2]);
                jdbc.update("DELETE FROM public.sources WHERE id=?", replacementSource);
                jdbc.update("DELETE FROM public.villages WHERE id=?", fixture[1]);
                jdbc.update("DELETE FROM public.governorates WHERE id=?", fixture[0]);
            });
        }
    }
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {CulturalHeritage.class, Source.class})
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses = {CulturalHeritageRepository.class, SourceRepository.class})
    @Import({CulturalHeritageCrudService.class, CulturalHeritageSourceLinksService.class})
    static class Config {
        @Bean CulturalHeritageSourceLinkRepository links(EntityManager em) { return new CulturalHeritageSourceLinkRepository(em); }
        @Bean SourceLookup sources(SourceRepository repository) { return new SourceLookupService(repository); }
        @Bean VillageLookupApi villages() { return mock(VillageLookupApi.class); }
        @Bean AuditWriter audits() { return mock(AuditWriter.class); }
        @Bean(destroyMethod = "close") ValidatorFactory validators() { return Validation.buildDefaultValidatorFactory(); }
        @Bean Validator validator(ValidatorFactory factory) { return factory.getValidator(); }
    }
    static class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            String url = "jdbc:postgresql://localhost:5432/qaryati_test_db";
            if (!"verified-test-db".equals(System.getenv("QARYATI_CULTURAL_CRUD_TESTS"))
                    || !Arrays.asList(env.getActiveProfiles()).contains("test")
                    || !url.equals(env.getProperty("spring.datasource.url"))) throw new IllegalStateException("Dedicated database opt-in required");
            try (var connection = DriverManager.getConnection(url, env.getRequiredProperty("spring.datasource.username"), env.getRequiredProperty("spring.datasource.password"));
                    var statement = connection.createStatement(); var result = statement.executeQuery("SELECT current_database(), current_user")) {
                if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()) || !result.next()
                        || !"qaryati_test_db".equals(result.getString(1)) || !env.getRequiredProperty("spring.datasource.username").equals(result.getString(2)))
                    throw new IllegalStateException("Database identity mismatch");
            } catch (SQLException failure) { throw new IllegalStateException("Database identity verification failed", failure); }
            env.getPropertySources().addFirst(new MapPropertySource("cultural-crud-test-safety", Map.of(
                    "spring.datasource.url", url, "spring.datasource.hikari.jdbc-url", url, "spring.flyway.enabled", "false",
                    "spring.jpa.hibernate.ddl-auto", "none", "spring.jpa.properties.hibernate.hbm2ddl.auto", "none",
                    "spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none")));
        }
    }
}
