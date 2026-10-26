package com.qaryati.population.entity;

import com.qaryati.common.exception.DuplicateResourceException;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "population_records", schema = "public", uniqueConstraints =
        @UniqueConstraint(name = "uq_population_observation", columnNames =
                {"village_id", "reference_year", "methodology", "source_id"}))
public class PopulationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "village_id", nullable = false)
    private Long villageId;
    @Column(name = "reference_year", nullable = false)
    private Integer referenceYear;
    @Column(name = "reference_date")
    private LocalDate referenceDate;
    @Column(name = "population_count", nullable = false)
    private Integer populationCount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Methodology methodology;
    @Column(name = "source_id", nullable = false)
    private Long sourceId;
    @Column(columnDefinition = "text")
    private String notes;
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PopulationRecord() { }

    public PopulationRecord(Long villageId, Integer referenceYear, LocalDate referenceDate,
                            Integer populationCount, Methodology methodology, Long sourceId, String notes) {
        replaceObservation(villageId, referenceYear, referenceDate, populationCount, methodology, sourceId, notes);
    }

    public void requireDraft() {
        if (verificationStatus != VerificationStatus.DRAFT) {
            throw new DuplicateResourceException("Only DRAFT population records may be updated or deleted");
        }
    }

    public void replaceObservation(Long villageId, Integer referenceYear, LocalDate referenceDate,
                                   Integer populationCount, Methodology methodology, Long sourceId, String notes) {
        requireDraft();
        if (villageId == null || villageId <= 0 || sourceId == null || sourceId <= 0
                || referenceYear == null || referenceYear < 1800 || referenceYear > 2200
                || populationCount == null || populationCount < 0 || methodology == null
                || (referenceDate != null && referenceDate.getYear() != referenceYear)) {
            throw new IllegalArgumentException("Invalid population observation");
        }
        this.villageId = villageId;
        this.referenceYear = referenceYear;
        this.referenceDate = referenceDate;
        this.populationCount = populationCount;
        this.methodology = methodology;
        this.sourceId = sourceId;
        this.notes = notes;
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = Instant.now(); }

    @PreRemove
    void guardDeletion() { requireDraft(); }

    public Long getId() { return id; }
    public Long getVillageId() { return villageId; }
    public Integer getReferenceYear() { return referenceYear; }
    public LocalDate getReferenceDate() { return referenceDate; }
    public Integer getPopulationCount() { return populationCount; }
    public Methodology getMethodology() { return methodology; }
    public Long getSourceId() { return sourceId; }
    public String getNotes() { return notes; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
