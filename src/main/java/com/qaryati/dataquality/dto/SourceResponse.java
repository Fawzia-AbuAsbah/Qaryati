
package com.qaryati.dataquality.dto;

import com.qaryati.dataquality.entity.Source;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SourceResponse(
        Long id,
        String title,
        String publisher,
        String author,
        String sourceType,
        String url,
        LocalDate publicationDate,
        LocalDate accessedAt,
        String citation,
        OffsetDateTime createdAt
) {

    public static SourceResponse fromEntity(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getTitle(),
                source.getPublisher(),
                source.getAuthor(),
                source.getSourceType(),
                source.getUrl(),
                source.getPublicationDate(),
                source.getAccessedAt(),
                source.getCitation(),
                source.getCreatedAt()
        );
    }
}
