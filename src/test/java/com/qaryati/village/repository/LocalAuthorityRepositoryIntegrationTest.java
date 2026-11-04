package com.qaryati.village.repository;

import com.qaryati.village.dto.LocalAuthorityResponse;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.LocalAuthority;
import com.qaryati.village.entity.LocalAuthorityType;
import com.qaryati.village.mapper.LocalAuthorityMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NOT authorized for execution until independent database/schema verification.
 * Plain Spring persistence context: no application startup, Boot, Flyway or SQL initializer.
 * Every fixture and JDBC statement joins the rollback-only test transaction.
 */
@SpringJUnitConfig
@ContextConfiguration(classes = LocalAuthorityTestDatabaseSupport.PersistenceConfiguration.class,
        initializers = LocalAuthorityTestDatabaseSupport.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=false", "spring.flyway.clean-disabled=true",
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
@Rollback
class LocalAuthorityRepositoryIntegrationTest {
    @Autowired private LocalAuthorityRepository repository;
    @Autowired private GovernorateRepository governorates;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(LocalAuthorityType.class)
    void persistsAndReloadsEveryTypeWithAllFields(LocalAuthorityType type) {
        Governorate governorate = governorate();
        LocalAuthority authority = authority(governorate, uniqueArabic());
        authority.setNameEn("English council");
        authority.setAuthorityType(type);
        Long id = repository.saveAndFlush(authority).getId();
        assertNotNull(id);
        entityManager.clear();

        LocalAuthority found = repository.findById(id).orElseThrow();
        assertEquals(id, found.getId());
        assertEquals(governorate.getId(), found.getGovernorate().getId());
        assertEquals(authority.getNameAr(), found.getNameAr());
        assertEquals("English council", found.getNameEn());
        assertEquals(type, found.getAuthorityType());
        assertEquals(type.name(), jdbc.queryForObject(
                "SELECT authority_type FROM public.local_authorities WHERE id=?", String.class, id));
    }

    @Test
    void allowsNullEnglishName() {
        LocalAuthority saved = repository.saveAndFlush(authority(governorate(), uniqueArabic()));
        Long id = saved.getId();
        entityManager.clear();
        assertNull(repository.findById(id).orElseThrow().getNameEn());
    }

    @Test
    void acceptsMaximumNameLengths() {
        String name = "م".repeat(144) + UUID.randomUUID();
        LocalAuthority authority = authority(governorate(), name);
        authority.setNameEn("x".repeat(180));
        Long id = repository.saveAndFlush(authority).getId();
        entityManager.clear();
        LocalAuthority found = repository.findById(id).orElseThrow();
        assertEquals(name, found.getNameAr());
        assertEquals(180, found.getNameAr().length());
        assertEquals("x".repeat(180), found.getNameEn());
    }

    @Test
    void filtersByGovernorateAndReturnsEmptyForAnotherExistingGovernorate() {
        Governorate first = governorate();
        Governorate second = governorate();
        Governorate empty = governorate();
        Long one = repository.saveAndFlush(authority(first, uniqueArabic())).getId();
        Long two = repository.saveAndFlush(authority(first, uniqueArabic())).getId();
        Long other = repository.saveAndFlush(authority(second, uniqueArabic())).getId();
        entityManager.clear();

        assertEquals(java.util.Set.of(one, two), repository.findByGovernorate(first).stream()
                .map(LocalAuthority::getId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.List.of(other), repository.findByGovernorate(second).stream()
                .map(LocalAuthority::getId).toList());
        assertTrue(repository.findByGovernorate(empty).isEmpty());
    }

    @Test
    void rejectsDuplicateArabicNameInSameGovernorate() {
        Governorate governorate = governorate();
        String name = uniqueArabic();
        repository.saveAndFlush(authority(governorate, name));
        assertState("23505", () -> repository.saveAndFlush(authority(governorate, name)));
    }

    @Test
    void allowsSameArabicNameInDifferentGovernorates() {
        String name = uniqueArabic();
        Long first = repository.saveAndFlush(authority(governorate(), name)).getId();
        Long second = repository.saveAndFlush(authority(governorate(), name)).getId();
        entityManager.clear();
        assertNotEquals(first, second);
        assertEquals(name, repository.findById(first).orElseThrow().getNameAr());
        assertEquals(name, repository.findById(second).orElseThrow().getNameAr());
    }

    @Test
    void existenceQueriesScopeGovernorateAndExcludeCurrentId() {
        Governorate first = governorate();
        Governorate second = governorate();
        LocalAuthority own = repository.saveAndFlush(authority(first, uniqueArabic()));
        LocalAuthority peer = repository.saveAndFlush(authority(first, uniqueArabic()));
        LocalAuthority elsewhere = repository.saveAndFlush(authority(second, uniqueArabic()));
        entityManager.clear();

        assertTrue(repository.existsByGovernorateAndNameAr(first, own.getNameAr()));
        assertFalse(repository.existsByGovernorateAndNameAr(first, uniqueArabic()));
        assertFalse(repository.existsByGovernorateAndNameAr(first, elsewhere.getNameAr()));
        assertFalse(repository.existsByGovernorateAndNameArAndIdNot(first, own.getNameAr(), own.getId()));
        assertTrue(repository.existsByGovernorateAndNameArAndIdNot(first, peer.getNameAr(), own.getId()));
        assertFalse(repository.existsByGovernorateAndNameArAndIdNot(first, elsewhere.getNameAr(), own.getId()));
        assertFalse(repository.existsByGovernorateAndNameArAndIdNot(first, uniqueArabic(), own.getId()));
    }

    @Test
    void updatesWithoutChangingOwnName() {
        LocalAuthority saved = repository.saveAndFlush(authority(governorate(), uniqueArabic()));
        saved.setNameEn("Replacement");
        saved.setAuthorityType(LocalAuthorityType.OTHER);
        repository.saveAndFlush(saved);
        entityManager.clear();
        LocalAuthority found = repository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getNameAr(), found.getNameAr());
        assertEquals("Replacement", found.getNameEn());
        assertEquals(LocalAuthorityType.OTHER, found.getAuthorityType());
    }

    @Test
    void rejectsDuplicateNameOnUpdate() {
        Governorate governorate = governorate();
        LocalAuthority first = repository.saveAndFlush(authority(governorate, uniqueArabic()));
        LocalAuthority second = repository.saveAndFlush(authority(governorate, uniqueArabic()));
        second.setNameAr(first.getNameAr());
        assertState("23505", () -> repository.saveAndFlush(second));
    }

    @Test
    void rejectsInvalidAuthorityTypeInDatabase() {
        Long governorateId = governorate().getId();
        assertState("23514", () -> insert(governorateId, uniqueArabic(), null, "INVALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"governorate_id", "name_ar", "authority_type"})
    void rejectsNullRequiredColumnsInDatabase(String column) {
        Long governorateId = governorate().getId();
        assertState("23502", () -> insert(column.equals("governorate_id") ? null : governorateId,
                column.equals("name_ar") ? null : uniqueArabic(), null,
                column.equals("authority_type") ? null : "MUNICIPALITY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name_ar", "name_en"})
    void rejectsOversizedNamesInDatabase(String column) {
        Long governorateId = governorate().getId();
        assertState("22001", () -> insert(governorateId,
                column.equals("name_ar") ? "x".repeat(181) : uniqueArabic(),
                column.equals("name_en") ? "x".repeat(181) : null, "OTHER"));
    }

    @Test
    void rejectsNonexistentGovernorateInDatabase() {
        governorate(); // Every invocation creates its own fixtures.
        Long missing = jdbc.queryForObject("""
                SELECT candidate FROM generate_series(-1000::bigint, -1::bigint) AS candidate
                WHERE NOT EXISTS (SELECT 1 FROM public.governorates WHERE id=candidate)
                LIMIT 1
                """, Long.class);
        assertNotNull(missing);
        assertState("23503", () -> insert(missing, uniqueArabic(), null, "OTHER"));
    }

    @Test
    void deletesUnreferencedAuthorityWithoutDeletingGovernorate() {
        Governorate governorate = governorate();
        LocalAuthority saved = repository.saveAndFlush(authority(governorate, uniqueArabic()));
        Long id = saved.getId();
        repository.delete(saved);
        repository.flush();
        entityManager.clear();
        assertTrue(repository.findById(id).isEmpty());
        assertTrue(governorates.findById(governorate.getId()).isPresent());
    }

    @Test
    void restrictsDeletionWhenVillageReferencesAuthority() {
        Governorate governorate = governorate();
        LocalAuthority saved = repository.saveAndFlush(authority(governorate, uniqueArabic()));
        village(governorate.getId(), saved.getId());
        assertState("23503", () -> {
            repository.delete(saved);
            repository.flush();
        });
    }

    @Test
    void rejectsVillageAuthorityFromDifferentGovernorate() {
        LocalAuthority saved = repository.saveAndFlush(authority(governorate(), uniqueArabic()));
        Long otherGovernorate = governorate().getId();
        assertState("23503", () -> village(otherGovernorate, saved.getId()));
    }

    @Test
    void lazilyLoadsGovernorateAndMapsResponseInsideTransaction() {
        Governorate governorate = governorate();
        LocalAuthority saved = repository.saveAndFlush(authority(governorate, uniqueArabic()));
        Long governorateId = governorate.getId();
        String governorateName = governorate.getNameAr();
        Long id = saved.getId();
        entityManager.clear();

        LocalAuthority found = repository.findById(id).orElseThrow();
        assertFalse(Hibernate.isInitialized(found.getGovernorate()));
        assertEquals(new LocalAuthorityResponse(id, governorateId, saved.getNameAr(), null,
                LocalAuthorityType.MUNICIPALITY), new LocalAuthorityMapper().toResponse(found));
        assertEquals(governorateName, found.getGovernorate().getNameAr());
        assertTrue(Hibernate.isInitialized(found.getGovernorate()));
    }

    private Governorate governorate() {
        Governorate governorate = new Governorate();
        governorate.setNameAr(uniqueArabic());
        return governorates.saveAndFlush(governorate);
    }

    private LocalAuthority authority(Governorate governorate, String name) {
        LocalAuthority authority = new LocalAuthority();
        authority.setGovernorate(governorate);
        authority.setNameAr(name);
        authority.setAuthorityType(LocalAuthorityType.MUNICIPALITY);
        return authority;
    }

    private String uniqueArabic() {
        return "مجلس-" + UUID.randomUUID();
    }

    private void insert(Long governorateId, String nameAr, String nameEn, String type) {
        jdbc.update("""
                INSERT INTO public.local_authorities(governorate_id,name_ar,name_en,authority_type)
                VALUES (?,?,?,?)
                """, governorateId, nameAr, nameEn, type);
    }

    private void village(Long governorateId, Long authorityId) {
        jdbc.update("""
                INSERT INTO public.villages(governorate_id,local_authority_id,name_ar,location_description)
                VALUES (?,?,?,?)
                """, governorateId, authorityId, uniqueArabic(), "Local Authority isolated test fixture");
    }

    private void assertState(String expected, Runnable operation) {
        DataIntegrityViolationException failure =
                assertThrows(DataIntegrityViolationException.class, operation::run);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                assertEquals(expected, sql.getSQLState());
                return;
            }
        }
        fail("Expected PostgreSQL SQLSTATE " + expected);
        // No SQL is executed after the failed statement; Spring rolls back the transaction.
    }
}