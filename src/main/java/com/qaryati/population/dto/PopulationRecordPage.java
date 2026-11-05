package com.qaryati.population.dto;

import java.util.List;

public record PopulationRecordPage(List<PopulationRecordResponse> content, int page, int size,
                                   long totalElements, int totalPages) {
    public PopulationRecordPage { content = List.copyOf(content); }
}
