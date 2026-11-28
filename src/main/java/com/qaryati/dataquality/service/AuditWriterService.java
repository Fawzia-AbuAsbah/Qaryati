package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.HeritageSiteAuditSnapshot;
import com.qaryati.dataquality.api.AuditSnapshot;
import com.qaryati.dataquality.api.AuditWriter;
import com.qaryati.dataquality.api.HistoricalEventAuditSnapshot;
import com.qaryati.dataquality.api.AgriculturalProductAuditSnapshot;
import com.qaryati.dataquality.api.VillageAgricultureAuditSnapshot;
import com.qaryati.dataquality.api.EconomicActivityAuditSnapshot;
import com.qaryati.dataquality.api.CommunityFacilityAuditSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditWriterService implements AuditWriter {
    @Override @Transactional
    public void recordCulturalHeritage(Long culturalHeritageId, AuditAction action,
            com.qaryati.dataquality.api.CulturalHeritageAuditSnapshot before,
            com.qaryati.dataquality.api.CulturalHeritageAuditSnapshot after) {
        if (action == null || switch (action) {
            case CREATE -> before != null || after == null;
            case UPDATE -> before == null || after == null;
            case DELETE -> before == null || after != null;
        }) throw new IllegalArgumentException("Invalid cultural heritage audit state");
        auditLogService.record("CULTURAL_HERITAGE", culturalHeritageId, action.name(), culturalSnapshot(before),
                culturalSnapshot(after), "Domain record " + action.name());
    }

    private Map<String, Object> culturalSnapshot(com.qaryati.dataquality.api.CulturalHeritageAuditSnapshot value) {
        if (value == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("villageId", value.villageId());
        data.put("name", value.name());
        data.put("heritageType", value.heritageType());
        data.put("description", value.description());
        data.put("sourceIds", value.sourceIds());
        data.put("verificationStatus", value.verificationStatus());
        return data;
    }
    @Override @Transactional
    public void recordVillageHistory(Long historyId, AuditAction action,
            com.qaryati.dataquality.api.VillageHistoryAuditSnapshot before,
            com.qaryati.dataquality.api.VillageHistoryAuditSnapshot after) {
        if (action == null || switch (action) {
            case CREATE -> before != null || after == null;
            case UPDATE -> before == null || after == null;
            case DELETE -> before == null || after != null;
        }) throw new IllegalArgumentException("Invalid history audit state");
        auditLogService.record("VILLAGE_HISTORY", historyId, action.name(), historySnapshot(before),
                historySnapshot(after), "Domain record " + action.name());
    }
    private Map<String, Object> historySnapshot(com.qaryati.dataquality.api.VillageHistoryAuditSnapshot value) {
        if (value == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("villageId", value.villageId());
        data.put("overview", value.overview());
        data.put("sourceIds", value.sourceIds());
        data.put("verificationStatus", value.verificationStatus());
        return data;
    }

    private final AuditLogService auditLogService;

    public AuditWriterService(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

    @Override @Transactional
    public void recordHeritageSite(Long siteId, AuditAction action,
            HeritageSiteAuditSnapshot before, HeritageSiteAuditSnapshot after) {
        if (action == null || switch (action) {
            case CREATE -> before != null || after == null;
            case UPDATE -> before == null || after == null;
            case DELETE -> before == null || after != null;
        }) throw new IllegalArgumentException("Invalid site audit before/after state");
        auditLogService.record("HERITAGE_SITE", siteId, action.name(), siteSnapshot(before),
                siteSnapshot(after), "Domain record " + action.name());
    }

    private Map<String, Object> siteSnapshot(HeritageSiteAuditSnapshot value) {
        if (value == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("villageId", value.villageId());
        data.put("name", value.name());
        data.put("siteType", value.siteType());
        if (value.description() != null) data.put("description", value.description());
        if (value.latitude() != null) data.put("latitude", value.latitude());
        if (value.longitude() != null) data.put("longitude", value.longitude());
        if (value.conditionNotes() != null) data.put("conditionNotes", value.conditionNotes());
        data.put("sourceIds", value.sourceIds());
        data.put("verificationStatus", value.verificationStatus());
        return data;
    }

    @Override
    @Transactional
    public void recordHistoricalEvent(Long eventId, AuditAction action,
            HistoricalEventAuditSnapshot before, HistoricalEventAuditSnapshot after) {
        if (action == null || switch (action) {
            case CREATE -> before != null || after == null;
            case UPDATE -> before == null || after == null;
            case DELETE -> before == null || after != null;
        }) throw new IllegalArgumentException("Invalid event audit before/after state");
        auditLogService.record("HISTORICAL_EVENT", eventId, action.name(), eventSnapshot(before),
                eventSnapshot(after), "Domain record " + action.name());
    }

    private Map<String, Object> eventSnapshot(HistoricalEventAuditSnapshot value) {
        if (value == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("villageId", value.villageId());
        data.put("title", value.title());
        if (value.eventDate() != null) data.put("eventDate", value.eventDate().toString());
        if (value.yearFrom() != null) data.put("yearFrom", value.yearFrom());
        if (value.yearTo() != null) data.put("yearTo", value.yearTo());
        data.put("datePrecision", value.datePrecision());
        data.put("sourceIds", value.sourceIds());
        if (value.verificationStatus() != null) data.put("verificationStatus", value.verificationStatus());
        return data;
    }

    @Override @Transactional
    public void recordAgriculturalProduct(Long productId, AuditAction action,
            AgriculturalProductAuditSnapshot before, AgriculturalProductAuditSnapshot after) {
        requireCrudState(action, before, after, "agricultural product");
        auditLogService.record("AGRICULTURAL_PRODUCT", productId, action.name(), productSnapshot(before),
                productSnapshot(after), "Domain record " + action.name());
    }

    private Map<String, Object> productSnapshot(AgriculturalProductAuditSnapshot value) {
        if (value == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nameAr", value.nameAr());
        if (value.nameEn() != null) data.put("nameEn", value.nameEn());
        data.put("productType", value.productType());
        return data;
    }

    @Override @Transactional
    public void recordVillageAgriculture(Long relationId, AuditAction action,
            VillageAgricultureAuditSnapshot before, VillageAgricultureAuditSnapshot after) {
        requireCrudState(action, before, after, "village agriculture");
        auditLogService.record("VILLAGE_AGRICULTURE", relationId, action.name(), relationSnapshot(before),
                relationSnapshot(after), "Domain record " + action.name());
    }

    private Map<String, Object> relationSnapshot(VillageAgricultureAuditSnapshot value) {
        if (value == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("villageId", value.villageId());
        data.put("productId", value.productId());
        if (value.importance() != null) data.put("importance", value.importance());
        return data;
    }

    @Override @Transactional
    public void recordEconomicActivity(Long activityId, AuditAction action,
            EconomicActivityAuditSnapshot before, EconomicActivityAuditSnapshot after) {
        requireCrudState(action, before, after, "economic activity");
        auditLogService.record("ECONOMIC_ACTIVITY", activityId, action.name(), activitySnapshot(before),
                activitySnapshot(after), "Domain record " + action.name());
    }

    private Map<String, Object> activitySnapshot(EconomicActivityAuditSnapshot value) {
        if (value == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("villageId", value.villageId());
        data.put("name", value.name());
        data.put("activityType", value.activityType());
        return data;
    }

    @Override @Transactional
    public void recordCommunityFacility(Long facilityId, AuditAction action,
            CommunityFacilityAuditSnapshot before, CommunityFacilityAuditSnapshot after) {
        requireCrudState(action, before, after, "community facility");
        auditLogService.record("COMMUNITY_FACILITY", facilityId, action.name(), facilitySnapshot(before),
                facilitySnapshot(after), "Domain record " + action.name());
    }

    private Map<String, Object> facilitySnapshot(CommunityFacilityAuditSnapshot value) {
        if (value == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("villageId", value.villageId());
        data.put("name", value.name());
        data.put("facilityType", value.facilityType());
        if (value.latitude() != null) {
            data.put("latitude", value.latitude());
            data.put("longitude", value.longitude());
        }
        return data;
    }

    private static void requireCrudState(AuditAction action, Object before, Object after, String type) {
        if (action == null || switch (action) {
            case CREATE -> before != null || after == null;
            case UPDATE -> before == null || after == null;
            case DELETE -> before == null || after != null;
        }) throw new IllegalArgumentException("Invalid " + type + " audit state");
    }

    @Override
    @Transactional
    public void record(String entityType, Long entityId, AuditAction action,
                       AuditSnapshot before, AuditSnapshot after) {
        if (entityType == null || !entityType.matches("[A-Z][A-Z0-9_]{0,79}")) {
            throw new IllegalArgumentException("Audit entity type must be an uppercase identifier");
        }
        if (action == null) { throw new IllegalArgumentException("Audit action is required"); }
        auditLogService.record(entityType, entityId, action.name(), snapshot(before), snapshot(after),
                "Domain record " + action.name());
    }

    private Map<String, Object> snapshot(AuditSnapshot snapshot) {
        if (snapshot == null) { return null; }
        Map<String, Object> data = new LinkedHashMap<>();
        if (snapshot.villageId() != null) { data.put("villageId", snapshot.villageId()); }
        if (snapshot.sourceId() != null) { data.put("sourceId", snapshot.sourceId()); }
        if (snapshot.referenceYear() != null) { data.put("referenceYear", snapshot.referenceYear()); }
        if (snapshot.populationCount() != null) { data.put("populationCount", snapshot.populationCount()); }
        return data;
    }
}
