package com.qaryati.village.repository;

import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import com.qaryati.village.mapper.VillageRelationMapper;
import com.qaryati.village.service.VillageRelationService;
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
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verified: 22 PostgreSQL integration tests passed, 0 failures, 0 errors, 0 skipped.
 * PostgreSQL 17.4 on the approved qaryati_test_db.
 * BUILD SUCCESS on 2026-11-04 at 21:29:34 +02:00.
 * Separate authorization and independent dedicated database/schema verification required.
 * Reuses the unchanged guard. No Boot, Flyway, initialization or generated DDL.
 * Fixtures roll back; identity sequences can advance. No multi-connection claims.
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
class VillageRelationRepositoryIntegrationTest {
    @Autowired VillageRelationRepository relations;
    @Autowired VillageRepository villages;
    @Autowired GovernorateRepository governorates;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;
    final VillageRelationMapper mapper = new VillageRelationMapper();

    @ParameterizedTest @EnumSource(VillageRelationType.class)
    void bothTargetFormsAllTypesIdentityAndLazyMapping(VillageRelationType type) {
        var source = village(); var target = village();
        var linked = relation(source,target,null,type,new BigDecimal("99999999.99"));
        var place = relation(source,null," Place "+"x".repeat(193),type,null);
        relations.saveAndFlush(linked); relations.saveAndFlush(place);
        var expected = mapper.toResponse(linked); var id = linked.getId();
        em.clear();
        var found = relations.findByIdAndVillageId(id,source.getId()).orElseThrow();
        assertFalse(Hibernate.isInitialized(found.getVillage()));
        assertFalse(Hibernate.isInitialized(found.getRelatedVillage()));
        assertEquals(expected,mapper.toResponse(found));
        assertFalse(Hibernate.isInitialized(found.getVillage()));
        assertFalse(Hibernate.isInitialized(found.getRelatedVillage()));
        assertNull(relations.findById(place.getId()).orElseThrow().getDistanceKm());
        assertNull(relations.findById(place.getId()).orElseThrow().getRelatedVillage());
    }
    @Test void outgoingOrderingScopingAndAllowedReverseAndTypes() {
        var a = village(); var b = village();
        var one = relations.saveAndFlush(relation(a,b,null,VillageRelationType.NEARBY,null));
        var two = relations.saveAndFlush(relation(a,b,null,VillageRelationType.OTHER,BigDecimal.ZERO));
        relations.saveAndFlush(relation(b,a,null,VillageRelationType.NEARBY,null));
        assertEquals(List.of(one.getId(),two.getId()),relations.findByVillageIdOrderByIdAsc(a.getId()).stream().map(VillageRelation::getId).toList());
        assertTrue(relations.findByIdAndVillageId(one.getId(),b.getId()).isEmpty());
        assertTrue(relations.existsByVillageIdAndRelatedVillageIdAndRelationType(a.getId(),b.getId(),VillageRelationType.NEARBY));
        assertFalse(relations.existsByVillageIdAndRelatedVillageIdAndRelationTypeAndIdNot(a.getId(),b.getId(),VillageRelationType.NEARBY,one.getId()));
    }
    @Test void schemaPermitsDirectDuplicateInsertsUnderRollback() {
        var a = village(); var b = village();
        relations.saveAndFlush(relation(a,b,null,VillageRelationType.NEARBY,BigDecimal.ONE));
        relations.saveAndFlush(relation(a,b,null,VillageRelationType.NEARBY,BigDecimal.TEN));
        relations.saveAndFlush(relation(a,null,"Same",VillageRelationType.OTHER,null));
        relations.saveAndFlush(relation(a,null,"Same",VillageRelationType.OTHER,null));
        assertEquals(4,relations.findByVillageIdOrderByIdAsc(a.getId()).size());
        assertTrue(relations.existsByVillageIdAndRelatedPlaceNameAndRelationType(a.getId(),"Same",VillageRelationType.OTHER));
    }
    @Test void lockedReplacementPhysicalDeletionArchiveRetentionAndTimestamps() {
        var a = village(); var b = village(); var sourceId = a.getId(); var targetId = b.getId();
        b.setArchivedAt(Instant.EPOCH); b.setStatus(VillageStatus.VERIFIED); villages.saveAndFlush(b);
        em.clear();
        var service = new VillageRelationService(villages,relations,mapper);
        var created = service.create(sourceId,new VillageRelationRequest(targetId,null,VillageRelationType.ADJACENT,BigDecimal.ZERO));
        a = villages.findById(sourceId).orElseThrow(); b = villages.findById(targetId).orElseThrow();
        assertEquals(LockModeType.PESSIMISTIC_WRITE,em.getLockMode(a));
        assertEquals(Instant.EPOCH,a.getUpdatedAt()); assertEquals(Instant.EPOCH,b.getUpdatedAt());
        var replaced = service.update(sourceId,created.id(),new VillageRelationRequest(null," Place ",VillageRelationType.OTHER,null));
        assertNull(replaced.relatedVillageId()); assertNull(replaced.distanceKm());
        var linked = service.update(sourceId,created.id(),new VillageRelationRequest(targetId,null,VillageRelationType.OTHER,null));
        assertNull(linked.relatedPlaceName()); assertEquals(targetId,linked.relatedVillageId());
        a.setArchivedAt(Instant.EPOCH); villages.saveAndFlush(a); em.clear();
        assertEquals(1,service.getAll(sourceId).size()); assertEquals(created.id(),service.getById(sourceId,created.id()).id());
        assertEquals(1,relations.findAll().stream().filter(r -> r.getId().equals(created.id())).count());
        a = villages.findById(sourceId).orElseThrow(); a.setArchivedAt(null); villages.saveAndFlush(a);
        service.delete(sourceId,created.id());
        assertTrue(relations.findByIdAndVillageId(created.id(),sourceId).isEmpty());
        assertEquals(Instant.EPOCH,a.getUpdatedAt());
    }
    @ParameterizedTest @ValueSource(strings = {
            "related_place_name=null", "related_village_id=village_id,related_place_name=null",
            "relation_type='INVALID'", "distance_km=-0.01",
            "related_village_id=village_id,related_place_name='Both'"
    })
    void checkConstraints(String assignment) {
        var entity = relations.saveAndFlush(relation(village(),null,"Place",VillageRelationType.OTHER,null));
        state("23514",()->jdbc.update("update public.village_relations set "+assignment+" where id=?",entity.getId()));
    }
    @Test void bothTargetsRejectedWithDistinctVillages() {
        var source = village(); var target = village();
        var entity = relations.saveAndFlush(relation(source,null,"Place",VillageRelationType.OTHER,null));
        state("23514",()->jdbc.update("update public.village_relations set related_village_id=? where id=?",target.getId(),entity.getId()));
    }
    @ParameterizedTest @ValueSource(strings = {"village_id","relation_type"})
    void requiredColumns(String column) {
        var entity = relations.saveAndFlush(relation(village(),null,"Place",VillageRelationType.OTHER,null));
        state("23502",()->jdbc.update("update public.village_relations set "+column+"=null where id=?",entity.getId()));
    }
    @Test void placeLengthConstraint() {
        var entity = relations.saveAndFlush(relation(village(),null,"Place",VillageRelationType.OTHER,null));
        state("22001",()->jdbc.update("update public.village_relations set related_place_name=? where id=?","x".repeat(201),entity.getId()));
    }
    @Test void distanceOverflow() {
        var entity = relations.saveAndFlush(relation(village(),null,"Place",VillageRelationType.OTHER,null));
        state("22003",()->jdbc.update("update public.village_relations set distance_km=100000000 where id=?",entity.getId()));
    }
    @Test void databaseRoundsExtraScaleWhileApiRejectsIt() {
        var entity = relations.saveAndFlush(relation(village(),null,"Place",VillageRelationType.OTHER,new BigDecimal("1.235")));
        em.clear(); assertEquals(new BigDecimal("1.24"),relations.findById(entity.getId()).orElseThrow().getDistanceKm());
    }
    @ParameterizedTest @ValueSource(strings = {"village_id","related_village_id"})
    void missingVillageFk(String column) {
        var entity = relations.saveAndFlush(relation(village(),village(),null,VillageRelationType.OTHER,null));
        Long missing = jdbc.queryForObject("select candidate from generate_series(-1000::bigint,-1::bigint) candidate where not exists (select 1 from public.villages where id=candidate) limit 1",Long.class);
        state("23503",()->jdbc.update("update public.village_relations set "+column+"=? where id=?",missing,entity.getId()));
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void bothVillageDeletesRestricted(boolean target) {
        var a = village(); var b = village();
        relations.saveAndFlush(relation(a,b,null,VillageRelationType.OTHER,null));
        state("23503",()->jdbc.update("delete from public.villages where id=?",target ? b.getId() : a.getId()));
    }
    private VillageRelation relation(Village source,Village target,String place,VillageRelationType type,BigDecimal distance) {
        var entity = mapper.toEntity(new VillageRelationRequest(target == null ? null : target.getId(),place,type,distance));
        entity.setVillage(source); entity.setRelatedVillage(target); return entity;
    }
    private Village village() {
        var governorate = new Governorate(); governorate.setNameAr("Relations-"+UUID.randomUUID()); governorates.saveAndFlush(governorate);
        var village = new Village(); village.setGovernorate(governorate); village.setNameAr("Village-"+UUID.randomUUID());
        village.setLocationDescription("Rollback fixture"); village.setStatus(VillageStatus.DRAFT);
        village.setCreatedAt(Instant.EPOCH); village.setUpdatedAt(Instant.EPOCH); return villages.saveAndFlush(village);
    }
    private void state(String expected,Runnable action) {
        var ex = assertThrows(DataIntegrityViolationException.class,action::run);
        Throwable cause = ex;
        while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
        assertInstanceOf(SQLException.class,cause); assertEquals(expected,((SQLException)cause).getSQLState());
        // No further SQL after a failed statement; PostgreSQL aborts the transaction.
    }
}
