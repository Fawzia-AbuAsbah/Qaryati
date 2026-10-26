package com.qaryati.heritage.entity;

import com.qaryati.heritage.dto.HeritageSiteRequest;
import com.qaryati.heritage.validation.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "heritage_sites", schema = "public")
@ValidCoordinatePair
public class HeritageSite implements CoordinateFields {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull @Column(name = "village_id", nullable = false, updatable = false)
    private Long villageId;
    @NotNull @Size(max = 250) @Column(nullable = false, length = 250)
    private String name;
    @NotNull @Enumerated(EnumType.STRING) @Column(name = "site_type", nullable = false, length = 32)
    private HeritageSiteType siteType;
    @Column(columnDefinition = "text")
    private String description;
    @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;
    @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;
    @Column(name = "condition_notes", columnDefinition = "text")
    private String conditionNotes;
    @NotNull @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;

    protected HeritageSite() {}

    public static HeritageSite draft(HeritageSiteRequest request) {
        if (request == null) throw new IllegalArgumentException("Site request is required");
        HeritageSite site = new HeritageSite();
        site.villageId = request.villageId();
        site.reviseDraft(request);
        return site;
    }

    /** Local invariant only; does not authorize an update or deletion. */
    public void requireDraftMutation() {
        if (verificationStatus != VerificationStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT heritage sites may be mutated");
    }

    public void reviseDraft(HeritageSiteRequest request) {
        requireDraftMutation();
        if (request == null || !Objects.equals(villageId, request.villageId()))
            throw new IllegalArgumentException("A site cannot be moved to another village");
        if (request.villageId() == null || request.name() == null || request.name().length() > 250
                || request.siteType() == null
                || !new CoordinatePairValidator().isValid(request, null)
                || !validCoordinate(request.latitude(), 90) || !validCoordinate(request.longitude(), 180))
            throw new IllegalArgumentException("Invalid heritage site fields");
        name = request.name();
        siteType = request.siteType();
        description = request.description();
        latitude = request.latitude();
        longitude = request.longitude();
        conditionNotes = request.conditionNotes();
    }

    private static boolean validCoordinate(BigDecimal value, int bound) {
        return value == null || value.scale() <= 6 && value.precision() - (long) value.scale() <= 3
                && value.abs().compareTo(BigDecimal.valueOf(bound)) <= 0;
    }
    public Long getId() { return id; }
    public Long getVillageId() { return villageId; }
    public String getName() { return name; }
    public HeritageSiteType getSiteType() { return siteType; }
    public String getDescription() { return description; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getConditionNotes() { return conditionNotes; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
}
