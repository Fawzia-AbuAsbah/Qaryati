package com.qaryati.population.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.qaryati.population.entity.Methodology;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PopulationRecordRequest(
        @NotNull @Positive Long villageId,
        @NotNull @Min(1800) @Max(2200) Integer referenceYear,
        LocalDate referenceDate,
        @NotNull @PositiveOrZero Integer populationCount,
        @NotNull Methodology methodology,
        @NotNull @Positive Long sourceId,
        String notes) {

    @AssertTrue(message = "Reference date year must match referenceYear")
    public boolean isReferenceDateConsistent() {
        return referenceDate == null || referenceYear == null || referenceDate.getYear() == referenceYear;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field: " + name);
    }
}
