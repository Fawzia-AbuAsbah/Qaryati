package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.HeritageSiteAuditSnapshot;
import com.qaryati.dataquality.api.AuditSnapshot;
import com.qaryati.dataquality.api.AuditWriter;
import com.qaryati.dataquality.api.HistoricalEventAuditSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditWriterService implements AuditWriter {
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
