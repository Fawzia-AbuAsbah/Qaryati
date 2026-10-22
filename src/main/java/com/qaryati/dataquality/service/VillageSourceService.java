
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.entity.VillageSource;
import com.qaryati.dataquality.entity.VillageSourceId;
import com.qaryati.dataquality.repository.SourceRepository;
import com.qaryati.dataquality.repository.VillageExistsRepository;
import com.qaryati.dataquality.repository.VillageSourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class VillageSourceService {

    private final VillageSourceRepository villageSourceRepository;
    private final SourceRepository sourceRepository;
    private final VillageExistsRepository villageExistsRepository;
    private final AuditLogService auditLogService;

    public VillageSourceService(
            VillageSourceRepository villageSourceRepository,
            SourceRepository sourceRepository,
            VillageExistsRepository villageExistsRepository,
            AuditLogService auditLogService
    ) {
        this.villageSourceRepository = villageSourceRepository;
        this.sourceRepository = sourceRepository;
        this.villageExistsRepository = villageExistsRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<VillageSource> getSourcesForVillage(Long villageId) {
        return villageSourceRepository.findByIdVillageId(villageId);
    }

    @Transactional(readOnly = true)
    public List<VillageSource> getVillagesForSource(Long sourceId) {
        return villageSourceRepository.findByIdSourceId(sourceId);
    }

    @Transactional(readOnly = true)
    public VillageSource getVillageSource(
            Long villageId,
            Long sourceId
    ) {
        VillageSourceId id = new VillageSourceId(
                villageId,
                sourceId
        );

        return villageSourceRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Village-source link not found"
                        )
                );
    }

    @Transactional
    public VillageSource linkSourceToVillage(
            Long villageId,
            Long sourceId,
            String claimNote
    ) {

        if (villageId == null || sourceId == null
                || villageId <= 0 || sourceId <= 0) {
            throw new IllegalArgumentException(
                    "Village ID and Source ID must be positive"
            );
        }

        if (!villageExistsRepository.existsById(villageId)) {
            throw new NoSuchElementException(
                    "Village not found: " + villageId
            );
        }

        if (!sourceRepository.existsById(sourceId)) {
            throw new NoSuchElementException(
                    "Source not found: " + sourceId
            );
        }

        VillageSourceId id = new VillageSourceId(
                villageId,
                sourceId
        );

        if (villageSourceRepository.existsById(id)) {
            throw new IllegalStateException(
                    "Source is already linked to this village"
            );
        }

        VillageSource link = new VillageSource(
                villageId,
                sourceId,
                claimNote
        );

        VillageSource saved =
                villageSourceRepository.saveAndFlush(link);

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("villageId", villageId);
        afterData.put("sourceId", sourceId);

        auditLogService.record(
                "VILLAGE_SOURCE",
                villageId,
                "CREATE",
                null,
                afterData,
                "Source linked to village"
        );

        return saved;
    }
}
