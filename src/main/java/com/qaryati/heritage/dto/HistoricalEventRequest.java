package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.DatePrecision;
import com.qaryati.heritage.validation.EventDateFields;
import com.qaryati.heritage.validation.ValidEventDate;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@ValidEventDate
public record HistoricalEventRequest(
        @NotNull @Positive Long villageId,
        @NotBlank @Size(max = 250) String title,
        @NotBlank String description,
        LocalDate eventDate,
        @Min(1) @Max(2200) Integer yearFrom,
        @Min(1) @Max(2200) Integer yearTo,
        @NotNull DatePrecision datePrecision,
        @NotEmpty java.util.List<@NotNull @Positive Long> sourceIds) implements EventDateFields {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }
    public LocalDate getEventDate() { return eventDate; }
    public Integer getYearFrom() { return yearFrom; }
    public Integer getYearTo() { return yearTo; }
    public DatePrecision getDatePrecision() { return datePrecision; }
}
