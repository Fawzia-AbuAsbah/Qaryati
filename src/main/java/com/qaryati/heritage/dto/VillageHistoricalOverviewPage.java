package com.qaryati.heritage.dto;
import java.util.List;
public record VillageHistoricalOverviewPage(List<VillageHistoricalOverviewResponse> content, int page, int size,
        long totalElements, int totalPages) {
    public VillageHistoricalOverviewPage { content = List.copyOf(content); }
}
