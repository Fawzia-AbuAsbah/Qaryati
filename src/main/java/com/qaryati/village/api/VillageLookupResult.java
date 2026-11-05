package com.qaryati.village.api;

/** Immutable village facts; status and archive state do not establish consumer eligibility. */
public record VillageLookupResult(Long villageId, VillageLookupStatus status, boolean archived) {}
