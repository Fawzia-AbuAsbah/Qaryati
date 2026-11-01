package com.qaryati.heritage.entity;

import com.qaryati.heritage.dto.CulturalHeritageRequest;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;

@Entity
@Table(name = "cultural_heritage", schema = "public", uniqueConstraints =
        @UniqueConstraint(columnNames = {"village_id", "name", "heritage_type"}))
public class CulturalHeritage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull @Column(name = "village_id", nullable = false, updatable = false)
    private Long villageId;
    @NotNull @Size(max = 200) @Column(nullable = false, length = 200)
    private String name;
    @NotNull @Enumerated(EnumType.STRING)
    @Column(name = "heritage_type", nullable = false, length = 25)
    private CulturalHeritageType heritageType;
    @NotNull @Column(nullable = false, columnDefinition = "text")
    private String description;
    @NotNull @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;

    protected CulturalHeritage() {}

    public static CulturalHeritage draft(CulturalHeritageRequest request) {
        if (request == null) throw new IllegalArgumentException("Cultural heritage request is required");
        CulturalHeritage heritage = new CulturalHeritage();
        heritage.villageId = request.villageId();
        heritage.reviseDraft(request);
        return heritage;
    }

    /** Local invariant only; does not authorize an update or deletion. */
    public void requireDraftMutation() {
        if (verificationStatus != VerificationStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT cultural heritage records may be mutated");
    }

    public void reviseDraft(CulturalHeritageRequest request) {
        requireDraftMutation();
        if (request == null || !Objects.equals(villageId, request.villageId()))
            throw new IllegalArgumentException("Cultural heritage cannot be moved to another village");
        if (request.villageId() == null || request.name() == null || request.name().length() > 200
                || request.heritageType() == null || request.description() == null)
            throw new IllegalArgumentException("Invalid cultural heritage fields");
        name = request.name();
        heritageType = request.heritageType();
        description = request.description();
    }

    public Long getId() { return id; }
    public Long getVillageId() { return villageId; }
    public String getName() { return name; }
    public CulturalHeritageType getHeritageType() { return heritageType; }
    public String getDescription() { return description; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
}
