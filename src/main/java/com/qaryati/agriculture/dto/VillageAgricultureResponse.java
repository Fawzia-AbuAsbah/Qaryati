
package com.qaryati.agriculture.dto;

import com.qaryati.agriculture.entity.AgricultureImportance;

public record VillageAgricultureResponse(

        Long id,

        Long villageId,

        Long productId,

        String description,

        AgricultureImportance importance

) {
}
