package com.qaryati.village.api;

import java.util.Optional;

/** Read-only village state for other application modules. */
public interface VillageLookupApi {

    /**
     * Returns a state snapshot, including archived villages, or empty if the ID is missing.
     * The snapshot does not guarantee unchanged state before a later write and does not
     * decide whether a consumer may create or update its own records.
     *
     * @throws IllegalArgumentException if villageId is null or not positive
     */
    Optional<VillageLookupResult> findById(Long villageId);
}
