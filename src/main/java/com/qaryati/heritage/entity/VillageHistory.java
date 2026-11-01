package com.qaryati.heritage.entity;

import com.qaryati.heritage.dto.VillageHistoryRequest;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "village_histories", schema = "public")
public class VillageHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull @Column(name = "village_id", nullable = false, unique = true, updatable = false)
    private Long villageId;
    @NotBlank @Column(nullable = false, columnDefinition = "text")
    private String overview;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @NotNull @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;

    protected VillageHistory() {}

    public static VillageHistory draft(VillageHistoryRequest request) {
        if (request == null) throw new IllegalArgumentException("History request is required");
        VillageHistory history = new VillageHistory();
        history.villageId = request.villageId();
        history.reviseDraft(request);
        return history;
    }

    /** Local invariant only; does not authorize an update or deletion. */
    public void requireDraftMutation() {
        if (verificationStatus != VerificationStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT village histories may be mutated");
    }

    public void reviseDraft(VillageHistoryRequest request) {
        requireDraftMutation();
        if (request == null || !Objects.equals(villageId, request.villageId()))
            throw new IllegalArgumentException("A history cannot be moved to another village");
        if (request.villageId() == null || request.overview() == null || request.overview().isBlank())
            throw new IllegalArgumentException("Invalid village history fields");
        overview = request.overview();
    }

    @PrePersist
    void initializeTimestamp() { updatedAt = Instant.now(); }

    @PreUpdate
    void updateTimestamp() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getVillageId() { return villageId; }
    public String getOverview() { return overview; }
    public Instant getUpdatedAt() { return updatedAt; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
}
