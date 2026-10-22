
package com.qaryati.dataquality.dto;

import com.qaryati.dataquality.entity.VillageSource;

public record VillageSourceResponse(
        Long villageId,
        Long sourceId,
        String claimNote
) {

    public static VillageSourceResponse fromEntity(
            VillageSource link
    ) {
        return new VillageSourceResponse(
                link.getVillageId(),
                link.getSourceId(),
                link.getClaimNote()
        );
    }
}
