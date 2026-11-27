package com.qaryati.village.controller;

import com.qaryati.village.dto.PublicVillageDetailResponse;
import com.qaryati.village.dto.PublicVillagePageResponse;
import com.qaryati.village.service.VillageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/public/villages")
public class PublicVillageController {

    private static final Set<String> LIST_PARAMETERS = Set.of(
            "page", "size", "search", "governorateId", "localAuthorityId", "sortBy", "sortDir");

    private final VillageService service;

    public PublicVillageController(VillageService service) {
        this.service = service;
    }

    @GetMapping
    public PublicVillagePageResponse getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long governorateId,
            @RequestParam(required = false) Long localAuthorityId,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam Map<String, String> parameters) {
        rejectUnexpectedParameters(parameters, LIST_PARAMETERS);
        return PublicVillagePageResponse.from(service.getPublicActive(
                page, size, search, governorateId, localAuthorityId, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public PublicVillageDetailResponse getById(@PathVariable Long id, @RequestParam Map<String, String> parameters) {
        rejectUnexpectedParameters(parameters, Set.of());
        return service.getPublicById(id);
    }

    private static void rejectUnexpectedParameters(Map<String, String> parameters, Set<String> allowed) {
        if (!allowed.containsAll(parameters.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported public village query parameter");
        }
    }
}
