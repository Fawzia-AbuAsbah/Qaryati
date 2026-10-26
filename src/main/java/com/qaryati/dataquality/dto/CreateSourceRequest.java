
package com.qaryati.dataquality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;
import com.qaryati.dataquality.enums.SourceType;
import java.time.LocalDate;

public record CreateSourceRequest(

        @NotBlank
        @Size(max = 350)
        String title,

        @Size(max = 200)
        String publisher,

        @Size(max = 200)
        String author,

        @NotNull
        SourceType sourceType,

        @URL
        @Pattern(regexp = "(?i)^https?://[^\\s/]+(?:[/?#][^\\s]*)?$")
        String url,

        LocalDate publicationDate,

        LocalDate accessedAt,

        String citation
) {
}
