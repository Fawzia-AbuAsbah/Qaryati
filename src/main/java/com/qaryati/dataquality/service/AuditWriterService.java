package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.AuditSnapshot;
import com.qaryati.dataquality.api.AuditWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditWriterService implements AuditWriter {
    private final AuditLogService auditLogService;

    public AuditWriterService(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

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
