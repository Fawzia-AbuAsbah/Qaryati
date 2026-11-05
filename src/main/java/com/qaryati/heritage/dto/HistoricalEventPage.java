package com.qaryati.heritage.dto;
import java.util.List;
public record HistoricalEventPage(List<HistoricalEventResponse> content, int page, int size,
        long totalElements, int totalPages) {
    public HistoricalEventPage { content = List.copyOf(content); }
}
