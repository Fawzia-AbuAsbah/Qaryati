package com.qaryati.heritage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VillageHistoryRequest(@NotNull Long villageId, @NotBlank String overview) {}
