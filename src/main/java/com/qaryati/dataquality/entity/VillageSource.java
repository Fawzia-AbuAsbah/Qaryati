
package com.qaryati.dataquality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "village_sources", schema = "public")
public class VillageSource {

    @EmbeddedId
    private VillageSourceId id;

    @Column(name = "claim_note", columnDefinition = "text")
    private String claimNote;

    protected VillageSource() {
    }

    public VillageSource(
            Long villageId,
            Long sourceId,
            String claimNote
    ) {
        this.id = new VillageSourceId(villageId, sourceId);
        this.claimNote = claimNote;
    }

    public VillageSourceId getId() {
        return id;
    }

    public Long getVillageId() {
        return id.getVillageId();
    }

    public Long getSourceId() {
        return id.getSourceId();
    }

    public String getClaimNote() {
        return claimNote;
    }

    public void setClaimNote(String claimNote) {
        this.claimNote = claimNote;
    }
}

