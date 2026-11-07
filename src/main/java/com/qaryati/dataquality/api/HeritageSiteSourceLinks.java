package com.qaryati.dataquality.api;

import java.util.Collection;
import java.util.List;

/**
 * Existing-source associations only. Heritage owns authorization, ownership and DRAFT checks.
 * IDs must be positive; missing sites/sources throw NoSuchElementException.
 * Writes require a nonempty collection; duplicates are collapsed. Results are immutable,
 * ascending IDs. Writes join the caller's REQUIRED transaction; callers must flush new
 * JPA sites before invoking this SQL-backed contract and propagate failures.
 * Site deletion cascades links; referenced source deletion is restricted by the database.
 */
public interface HeritageSiteSourceLinks {
    /** One query; empty input returns an empty map, missing sites fail. */
    java.util.Map<Long, List<Long>> sourceIdsBySiteIds(Collection<Long> siteIds);
    List<Long> sourceIds(Long heritageSiteId);
    /** Fails with IllegalStateException when the site already has links. */
    void linkNewSite(Long heritageSiteId, Collection<Long> sourceIds);
    /** Complete replacement after the caller has authorized a DRAFT mutation. */
    void replaceSources(Long heritageSiteId, Collection<Long> sourceIds);
}
