package com.qaryati.village.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Same pagination envelope as editorial Village reads, with only public content and totals. */
public record PublicVillagePageResponse(
        List<PublicVillageResponse> content, int number, int size, long totalElements,
        int totalPages, int numberOfElements, boolean first, boolean last,
        boolean hasNext, boolean hasPrevious, boolean empty) {

    public PublicVillagePageResponse {
        content = List.copyOf(content);
    }

    public static PublicVillagePageResponse from(Page<PublicVillageResponse> page) {
        return new PublicVillagePageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.getNumberOfElements(),
                page.isFirst(), page.isLast(), page.hasNext(), page.hasPrevious(), page.isEmpty());
    }
}
