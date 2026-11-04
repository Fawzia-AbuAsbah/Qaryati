package com.qaryati.village.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/** Explicit HTTP contract, independent of Spring Data's PageImpl serialization. */
public record VillagePageResponse(
        List<VillageResponse> content, int number, int size, long totalElements,
        int totalPages, int numberOfElements, boolean first, boolean last,
        boolean hasNext, boolean hasPrevious, boolean empty) {

    public VillagePageResponse {
        content = List.copyOf(content);
    }

    public static VillagePageResponse from(Page<VillageResponse> page) {
        return new VillagePageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.getNumberOfElements(),
                page.isFirst(), page.isLast(), page.hasNext(), page.hasPrevious(), page.isEmpty());
    }
}
