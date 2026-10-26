
package com.qaryati.dataquality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class VillageSourceId implements Serializable {

    @Column(name = "village_id", nullable = false)
    private Long villageId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    protected VillageSourceId() {
    }

    public VillageSourceId(Long villageId, Long sourceId) {
        this.villageId = villageId;
        this.sourceId = sourceId;
    }

    public Long getVillageId() {
        return villageId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof VillageSourceId that)) {
            return false;
        }

        return Objects.equals(villageId, that.villageId)
                && Objects.equals(sourceId, that.sourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(villageId, sourceId);
    }
}
