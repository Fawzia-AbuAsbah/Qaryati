package com.qaryati.heritage.dto;
import java.util.List;
public record HeritageSitePage(List<HeritageSiteResponse> content, int page, int size,
        long totalElements, int totalPages) {
    public HeritageSitePage { content = List.copyOf(content); }
}
