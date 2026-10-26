package com.qaryati.population.service;

import com.qaryati.population.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/**
 * Fail-closed boundary until provider-owned authorization, reference lookup and
 * audit contracts exist. Reads are also blocked: draft visibility is not agreed.
 * No configuration flag or permissive fallback may enable these operations.
 */
@Service
public class PopulationRecordService {
    public PopulationRecordResponse create(PopulationRecordRequest request) { throw unavailable(); }
    public List<PopulationRecordResponse> getAll() { throw unavailable(); }
    public PopulationRecordResponse getById(Long id) { throw unavailable(); }
    public PopulationRecordResponse update(Long id, PopulationRecordRequest request) { throw unavailable(); }
    public void delete(Long id) { throw unavailable(); }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Population CRUD is blocked pending authorization, visibility, reference validation and audit contracts");
    }
}
