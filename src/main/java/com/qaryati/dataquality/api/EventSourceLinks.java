package com.qaryati.dataquality.api;

import java.util.Collection;
import java.util.List;

/**
 * Existing-source associations only. Heritage owns authorization, ownership and DRAFT checks.
 * IDs must be positive; missing events/sources throw NoSuchElementException.
 * Writes require a nonempty collection; duplicates are collapsed. Results are immutable,
 * ascending IDs. Writes join the caller's REQUIRED transaction; callers must flush new
 * JPA events before invoking this SQL-backed contract and propagate failures.
 * Event deletion cascades links; referenced source deletion is restricted by the database.
 */
public interface EventSourceLinks {
    List<Long> sourceIds(Long eventId);
    /** Fails with IllegalStateException when the event already has links. */
    void linkNewEvent(Long eventId, Collection<Long> sourceIds);
    /** Complete replacement after the caller has authorized a DRAFT mutation. */
    void replaceSources(Long eventId, Collection<Long> sourceIds);
}
