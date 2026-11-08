package com.qaryati.heritage.dto;
import java.util.List;
public record CulturalHeritageCrudPage(List<CulturalHeritageCrudResponse> content, int page, int size,
        long totalElements, int totalPages) {
    public CulturalHeritageCrudPage { content = List.copyOf(content); }
}
