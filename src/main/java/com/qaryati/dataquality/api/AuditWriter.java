package com.qaryati.dataquality.api;

/** Synchronous audit append using the current authenticated, enabled database user. */
public interface AuditWriter {
    /** REQUIRED; complete exact text snapshots, CREATE after only, UPDATE both, DELETE before only. */
    void recordCulturalHeritage(Long culturalHeritageId, AuditAction action,
            CulturalHeritageAuditSnapshot before, CulturalHeritageAuditSnapshot after);
    /** REQUIRED, atomic with caller mutation; CREATE after only, UPDATE both, DELETE before only. */
    void recordVillageHistory(Long historyId, AuditAction action,
            VillageHistoryAuditSnapshot before, VillageHistoryAuditSnapshot after);
    /** REQUIRED; CREATE requires after only, UPDATE both, DELETE before only. */
    void recordHeritageSite(Long siteId, AuditAction action,
            HeritageSiteAuditSnapshot before, HeritageSiteAuditSnapshot after);
    /** REQUIRED; CREATE requires after only, UPDATE both, DELETE before only. */
    void recordHistoricalEvent(Long eventId, AuditAction action,
            HistoricalEventAuditSnapshot before, HistoricalEventAuditSnapshot after);
    /**
     * Joins the caller's transaction (REQUIRED). The owning service must place its
     * mutation and this call in one transaction and propagate failures for atomic rollback.
     * Standalone calls create their own transaction. Null snapshots denote absent state.
     * entityType is a stable uppercase identifier, never user-entered text or a secret.
     * Target existence is owned by the caller; DELETE audits may outlive their target.
     */
    void record(String entityType, Long entityId, AuditAction action,
                AuditSnapshot before, AuditSnapshot after);
}
