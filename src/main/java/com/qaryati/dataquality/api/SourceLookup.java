package com.qaryati.dataquality.api;

/** Source existence only; no approval or evidential eligibility is implied. */
public interface SourceLookup {
    /** @throws IllegalArgumentException if sourceId is null or not positive */
    boolean existsById(Long sourceId);

    /**
     * @throws IllegalArgumentException if sourceId is null or not positive
     * @throws java.util.NoSuchElementException if the source is missing
     * Existence is a point-in-time check; the database FK remains the integrity guard.
     */
    void requireExists(Long sourceId);
}
