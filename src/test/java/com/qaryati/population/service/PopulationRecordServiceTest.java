package com.qaryati.population.service;

import com.qaryati.population.dto.PopulationRecordRequest;
import com.qaryati.population.entity.Methodology;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class PopulationRecordServiceTest {
    private final PopulationRecordService service = new PopulationRecordService();
    private final PopulationRecordRequest request = new PopulationRecordRequest(
            1L, 2020, null, 100, Methodology.CENSUS, 2L, null);

    @Test
    void everyOperationFailsClosedWithoutProviderContracts() {
        for (org.junit.jupiter.api.function.Executable operation : java.util.List.<org.junit.jupiter.api.function.Executable>of(
                () -> service.create(request), () -> service.getAll(), () -> service.getById(1L),
                () -> service.update(1L, request), () -> service.delete(1L))) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class, operation);
            assertEquals(503, exception.getStatusCode().value());
            assertTrue(exception.getReason().contains("blocked"));
        }
    }
}
