package com.qaryati.heritage.entity;

import com.qaryati.heritage.dto.HistoricalEventRequest;
import com.qaryati.heritage.validation.EventDateFields;
import com.qaryati.heritage.validation.ValidEventDate;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "historical_events", schema = "public")
@ValidEventDate
public class HistoricalEvent implements EventDateFields {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull @Column(name = "village_id", nullable = false, updatable = false)
    private Long villageId;
    @NotBlank @Size(max = 250) @Column(nullable = false, length = 250)
    private String title;
    @NotBlank @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "event_date")
    private LocalDate eventDate;
    @Min(1) @Max(2200) @Column(name = "year_from")
    private Integer yearFrom;
    @Min(1) @Max(2200) @Column(name = "year_to")
    private Integer yearTo;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "date_precision", nullable = false, length = 16)
    private DatePrecision datePrecision;
    @NotNull @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected HistoricalEvent() {}

    public static HistoricalEvent draft(HistoricalEventRequest request) {
        HistoricalEvent event = new HistoricalEvent();
        event.villageId = request.villageId();
        event.reviseDraft(request);
        return event;
    }

    /** Local invariant only; does not authorize an update or deletion. */
    public void requireDraftMutation() {
        if (verificationStatus != VerificationStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT historical events may be mutated");
    }

    public void reviseDraft(HistoricalEventRequest request) {
        requireDraftMutation();
        if (!java.util.Objects.equals(villageId, request.villageId()))
            throw new IllegalArgumentException("An event cannot be moved to another village");
        if (request.villageId() == null || request.title() == null || request.title().isBlank()
                || request.title().length() > 250 || request.description() == null
                || request.description().isBlank() || request.datePrecision() == null
                || !validYear(request.yearFrom()) || !validYear(request.yearTo())
                || !new com.qaryati.heritage.validation.EventDateValidator().isValid(request, null))
            throw new IllegalArgumentException("Invalid historical event fields");
        title = request.title();
        description = request.description();
        eventDate = request.eventDate();
        yearFrom = request.yearFrom();
        yearTo = request.yearTo();
        datePrecision = request.datePrecision();
    }

    private static boolean validYear(Integer year) { return year == null || year >= 1 && year <= 2200; }
    public Long getId() { return id; }
    public Long getVillageId() { return villageId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getEventDate() { return eventDate; }
    public Integer getYearFrom() { return yearFrom; }
    public Integer getYearTo() { return yearTo; }
    public DatePrecision getDatePrecision() { return datePrecision; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
