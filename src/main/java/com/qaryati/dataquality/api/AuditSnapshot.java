package com.qaryati.dataquality.api;

/**
 * Allowlisted Population metadata. Null fields are omitted from persisted snapshots.
 * Deliberately accepts no strings, arbitrary keys, nested objects, or entity instances:
 * passwords, tokens, URLs, citations and free-text notes must never be supplied.
 * Values must describe these fields, not encode unrelated sensitive information.
 * Extend this contract explicitly when another domain needs reviewed metadata.
 */
public record AuditSnapshot(Long villageId, Long sourceId, Integer referenceYear,
                            Integer populationCount) {}
