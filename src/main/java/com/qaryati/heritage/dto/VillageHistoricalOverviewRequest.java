package com.qaryati.heritage.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record VillageHistoricalOverviewRequest(@NotNull @Positive Long villageId,
        @NotBlank String overview, @NotEmpty List<@NotNull @Positive Long> sourceIds) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) { throw new IllegalArgumentException("Unsupported request field"); }
}
