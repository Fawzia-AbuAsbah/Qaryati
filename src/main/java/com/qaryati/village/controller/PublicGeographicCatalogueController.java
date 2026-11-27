package com.qaryati.village.controller;

import com.qaryati.village.dto.PublicGovernorateResponse;
import com.qaryati.village.dto.PublicLocalAuthorityResponse;
import com.qaryati.village.service.PublicGeographicCatalogueService;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/public")
public class PublicGeographicCatalogueController {
    private final PublicGeographicCatalogueService service;

    public PublicGeographicCatalogueController(PublicGeographicCatalogueService service) {
        this.service = service;
    }

    @GetMapping("/governorates")
    public List<PublicGovernorateResponse> getGovernorates(@RequestParam MultiValueMap<String, String> parameters) {
        rejectUnexpectedParameters(parameters, Set.of());
        return service.getGovernorates();
    }

    @GetMapping("/local-authorities")
    public List<PublicLocalAuthorityResponse> getLocalAuthorities(
            @RequestParam(required = false) Long governorateId,
            @RequestParam MultiValueMap<String, String> parameters) {
        rejectUnexpectedParameters(parameters, Set.of("governorateId"));
        if (parameters.containsKey("governorateId")
                && (parameters.get("governorateId").size() != 1 || governorateId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid governorateId filter");
        }
        return service.getLocalAuthorities(governorateId);
    }

    private static void rejectUnexpectedParameters(MultiValueMap<String, String> parameters, Set<String> allowed) {
        if (!allowed.containsAll(parameters.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported public catalogue query parameter");
        }
    }
}
