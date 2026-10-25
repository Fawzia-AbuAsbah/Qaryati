package com.qaryati.population.dto;

import com.qaryati.population.entity.Methodology;
import com.qaryati.population.entity.VerificationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record PopulationRecordResponse(Long id, Long villageId, Integer referenceYear,
        LocalDate referenceDate, Integer populationCount, Methodology methodology, Long sourceId,
        String notes, VerificationStatus verificationStatus, Instant createdAt, Instant updatedAt) { }
