
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.entity.VillageSource;
import com.qaryati.dataquality.entity.VillageSourceId;
import com.qaryati.dataquality.repository.SourceRepository;
import com.qaryati.dataquality.repository.VillageExistsRepository;
import com.qaryati.dataquality.repository.VillageSourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class VillageSourceService {

    private final VillageSourceRepository villageSourceRepository;
    private final SourceRepository sourceRepository;
    private final VillageExistsRepository villageExistsRepository;

    public VillageSourceService(
            VillageSourceRepository villageSourceRepository,
            SourceRepository sourceRepository,
            VillageExistsRepository villageExistsRepository
    ) {
        this.villageSourceRepository = villageSourceRepository;
        this.sourceRepository = sourceRepository;
        this.villageExistsRepository = villageExistsRepository;
    }

    // Get all sources linked to a village
    @Transactional(readOnly = true)
    public List<VillageSource> getSourcesForVillage(Long villageId) {
        return villageSourceRepository.findByIdVillageId(villageId);
    }

    // Get all villages linked to a source
    @Transactional(readOnly = true)
    public List<VillageSource> getVillagesForSource(Long sourceId) {
        return villageSourceRepository.findByIdSourceId(sourceId);
    }

    // Get a specific village-source link
    @Transactional(readOnly = true)
    public VillageSource getVillageSource(
            Long villageId,
            Long sourceId
    ) {
        VillageSourceId id = new VillageSourceId(villageId, sourceId);

        return villageSourceRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Village-source link not found"
                        )
                );
    }

    // Link a source to a village
    @Transactional
    public VillageSource linkSourceToVillage(
            Long villageId,
            Long sourceId,
            String claimNote
    ) {

        // Validate IDs
        if (villageId == null || sourceId == null
                || villageId <= 0 || sourceId <= 0) {
            throw new IllegalArgumentException(
                    "Village ID and Source ID must be positive"
            );
        }

        // Check if village exists
        if (!villageExistsRepository.existsById(villageId)) {
            throw new NoSuchElementException(
                    "Village not found: " + villageId
            );
        }

        // Check if source exists
        if (!sourceRepository.existsById(sourceId)) {
            throw new NoSuchElementException(
                    "Source not found: " + sourceId
            );
        }

        // Create composite ID
        VillageSourceId id = new VillageSourceId(
                villageId,
                sourceId
        );

        // Prevent duplicate links
        if (villageSourceRepository.existsById(id)) {
            throw new IllegalStateException(
                    "Source is already linked to this village"
            );
        }

        // Create and save link
        VillageSource link = new VillageSource(
                villageId,
                sourceId,
                claimNote
        );

        return villageSourceRepository.save(link);
    }
}
