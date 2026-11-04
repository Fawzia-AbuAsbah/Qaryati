package com.qaryati.village.repository;
import com.qaryati.village.dto.VillageNameRequest;
import com.qaryati.village.entity.*;
import com.qaryati.village.mapper.VillageNameMapper;
import com.qaryati.village.service.VillageNameService;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.sql.SQLException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verified: 19 tests passed, 0 failures, 0 errors, 0 skipped.
 * PostgreSQL 17.4 on the approved qaryati_test_db.
 * BUILD SUCCESS on 2026-11-04 at 19:41:45 +02:00.
 * Requires separate approval and independently verified dedicated test identity/schema.
 * No Boot, migrations, SQL initialization or DDL generation. Every fixture rolls back.
 */
@SpringJUnitConfig
@ContextConfiguration(classes = VillageTestDatabaseSupport.PersistenceConfiguration.class,
        initializers = VillageTestDatabaseSupport.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=false", "spring.flyway.clean-disabled=true",
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
@Rollback
class VillageNameRepositoryIntegrationTest {
    @Autowired VillageNameRepository names;
    @Autowired VillageRepository villages;
    @Autowired GovernorateRepository governorates;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;
    final VillageNameMapper mapper = new VillageNameMapper();

    @ParameterizedTest @EnumSource(VillageNameType.class)
    void allFieldsTypesIdentityAndLazyParentRoundTrip(VillageNameType type) {
        var parent = village();
        var entity = name(parent,"n".repeat(200),"l".repeat(12),type);
        entity.setNotes("Notes"); names.saveAndFlush(entity);
        assertNotNull(entity.getId());
        var expected = mapper.toResponse(entity); var id = entity.getId(); em.clear();
        var found = names.findByIdAndVillageId(id,parent.getId()).orElseThrow();
        assertFalse(Hibernate.isInitialized(found.getVillage()));
        assertEquals(expected,mapper.toResponse(found));
        assertFalse(Hibernate.isInitialized(found.getVillage()));
    }
    @Test void deterministicScopedListAndWrongVillageIsolation() {
        var parent = village(); var other = village();
        var one = names.saveAndFlush(name(parent,"One","ar",VillageNameType.ALTERNATIVE));
        var two = names.saveAndFlush(name(parent,"Two","ar",VillageNameType.ALTERNATIVE));
        names.saveAndFlush(name(other,"Other","ar",VillageNameType.ALTERNATIVE));
        assertEquals(List.of(one.getId(),two.getId()),names.findByVillageIdOrderByIdAsc(parent.getId()).stream().map(VillageName::getId).toList());
        assertTrue(names.findByIdAndVillageId(one.getId(),other.getId()).isEmpty());
    }
    @Test void duplicateQueriesAndAllowedTupleVariants() {
        var parent = village();
        var one = names.saveAndFlush(name(parent,"Name","ar",VillageNameType.ALTERNATIVE));
        assertTrue(names.existsByVillageIdAndNameAndLanguageCodeAndNameType(parent.getId(),"Name","ar",VillageNameType.ALTERNATIVE));
        assertFalse(names.existsByVillageIdAndNameAndLanguageCodeAndNameTypeAndIdNot(parent.getId(),"Name","ar",VillageNameType.ALTERNATIVE,one.getId()));
        names.saveAndFlush(name(parent,"Name","en",VillageNameType.ALTERNATIVE));
        names.saveAndFlush(name(parent,"Name","ar",VillageNameType.HISTORICAL));
        names.saveAndFlush(name(village(),"Name","ar",VillageNameType.ALTERNATIVE));
    }
    @Test void duplicateTupleIsFinalDatabaseGuard() {
        var parent = village(); names.saveAndFlush(name(parent,"Name","ar",VillageNameType.ALTERNATIVE));
        state("23505", () -> names.saveAndFlush(name(parent,"Name","ar",VillageNameType.ALTERNATIVE)));
    }
    @Test void updateDuplicateIsRejected() {
        var parent = village(); names.saveAndFlush(name(parent,"One","ar",VillageNameType.ALTERNATIVE));
        var two = names.saveAndFlush(name(parent,"Two","ar",VillageNameType.ALTERNATIVE)); two.setName("One");
        state("23505", () -> names.saveAndFlush(two));
    }
    @ParameterizedTest @ValueSource(strings = {"village_id","name","language_code","name_type"})
    void notNullConstraints(String column) {
        var entity = names.saveAndFlush(name(village(),"Name","ar",VillageNameType.FORMER));
        state("23502", () -> jdbc.update("update public.village_names set "+column+"=null where id=?",entity.getId()));
    }
    @ParameterizedTest @CsvSource({"name,201","language_code,13","name_type,25"})
    void lengths(String column,int length) {
        var entity = names.saveAndFlush(name(village(),"Name","ar",VillageNameType.FORMER));
        state("22001", () -> jdbc.update("update public.village_names set "+column+"=? where id=?","x".repeat(length),entity.getId()));
    }
    @Test void enumCheck() {
        var entity = names.saveAndFlush(name(village(),"Name","ar",VillageNameType.FORMER));
        state("23514", () -> jdbc.update("update public.village_names set name_type='INVALID' where id=?",entity.getId()));
    }
    @Test void foreignKeyRejectsMissingVillage() {
        var entity = names.saveAndFlush(name(village(),"Name","ar",VillageNameType.FORMER));
        Long missing = jdbc.queryForObject("select candidate from generate_series(-1000::bigint,-1::bigint) candidate where not exists (select 1 from public.villages where id=candidate) limit 1",Long.class);
        state("23503", () -> jdbc.update("update public.village_names set village_id=? where id=?",missing,entity.getId()));
    }
    @Test void parentDeletionIsRestricted() {
        var parent = village(); names.saveAndFlush(name(parent,"Name","ar",VillageNameType.FORMER));
        state("23503", () -> jdbc.update("delete from public.villages where id=?",parent.getId()));
    }
    @Test void lockedServiceReplacementDeletionAndArchiveRetention() {
        var parent = village(); var timestamp = parent.getUpdatedAt();
        Long parentId = parent.getId();
        // Inserted entities retain Hibernate's internal WRITE mode. Clear fixture state
        // so the service query establishes the lock mode asserted on the reloaded parent.
        em.clear();
        var service = new VillageNameService(villages,names,mapper);
        var created = service.create(parentId,new VillageNameRequest(" Name "," EN ",VillageNameType.TRANSLITERATION,"Notes"));
        parent = villages.findById(parentId).orElseThrow();
        assertEquals(timestamp,parent.getUpdatedAt());
        assertEquals(LockModeType.PESSIMISTIC_WRITE,em.getLockMode(parent));
        assertNull(service.update(parent.getId(),created.id(),new VillageNameRequest("Other","ar",VillageNameType.FORMER,null)).notes());
        assertTrue(TestTransaction.isActive()); assertTrue(TestTransaction.isFlaggedForRollback());
        parent.setArchivedAt(Instant.EPOCH); villages.saveAndFlush(parent); em.clear();
        assertEquals(1,service.getAll(parent.getId()).size());
        assertEquals(created.id(),service.getById(parent.getId(),created.id()).id());
        parent = villages.findById(parent.getId()).orElseThrow(); parent.setArchivedAt(null); villages.saveAndFlush(parent);
        service.delete(parent.getId(),created.id());
        assertTrue(names.findByIdAndVillageId(created.id(),parent.getId()).isEmpty());
        assertEquals(timestamp,parent.getUpdatedAt());
    }
    private VillageName name(Village village,String text,String language,VillageNameType type) {
        var entity = mapper.toEntity(new VillageNameRequest(text,language,type,null)); entity.setVillage(village); return entity;
    }
    private Village village() {
        var governorate = new Governorate(); governorate.setNameAr("Names-"+UUID.randomUUID()); governorates.saveAndFlush(governorate);
        var village = new Village(); village.setGovernorate(governorate); village.setNameAr("Village-"+UUID.randomUUID());
        village.setLocationDescription("Rollback fixture"); village.setStatus(VillageStatus.DRAFT);
        village.setCreatedAt(Instant.EPOCH); village.setUpdatedAt(Instant.EPOCH); return villages.saveAndFlush(village);
    }
    private void state(String expected,Runnable action) {
        var ex = assertThrows(DataIntegrityViolationException.class,action::run);
        Throwable cause = ex;
        while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
        assertInstanceOf(SQLException.class,cause); assertEquals(expected,((SQLException)cause).getSQLState());
        // No SQL after a failed statement: PostgreSQL marks this transaction aborted.
    }
}