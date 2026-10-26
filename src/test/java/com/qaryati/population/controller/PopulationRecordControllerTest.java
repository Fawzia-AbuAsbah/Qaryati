package com.qaryati.population.controller;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.population.service.PopulationRecordService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the real blocked service, so tests cannot accidentally enable CRUD with mocks. */
@WebMvcTest(PopulationRecordController.class)
@Import({PopulationRecordService.class, GlobalExceptionHandler.class, PopulationRecordExceptionHandler.class})
class PopulationRecordControllerTest {
    private static final String BASE = "/api/v1/population-records";
    private static final String JSON = """
            {"villageId":1,"referenceYear":2020,"populationCount":100,"methodology":"CENSUS","sourceId":2}
            """;
    @Autowired private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "GET_ONE", "POST", "PUT", "DELETE"})
    @WithMockUser
    void allAuthenticatedOperationsRemainBlocked(String operation) throws Exception {
        String path = path(operation);
        mvc.perform(request(method(operation), path).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("blocked")))
                .andExpect(jsonPath("$.path").value(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "GET_ONE", "POST", "PUT", "DELETE"})
    void unauthenticatedRequestsAreUnauthorized(String operation) throws Exception {
        mvc.perform(request(method(operation), path(operation)).with(csrf())
                        .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @WithMockUser
    void writesRequireCsrf(String operation) throws Exception {
        mvc.perform(request(method(operation), path(operation))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());
        mvc.perform(request(method(operation), path(operation)).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    @WithMockUser
    void invalidAndProtectedFieldsReturnCommonBadRequestShape(String operation) throws Exception {
        for (String json : java.util.List.of("{}", "{", JSON.replace("2020", "1799"),
                JSON.replace("2020", "2201"), JSON.replace("100", "-1"),
                JSON.replace("CENSUS", "INVALID"), JSON.replace("CENSUS", "census"),
                JSON.replace("\"sourceId\":2", "\"sourceId\":null"),
                JSON.replace("\"villageId\":1", "\"villageId\":0"),
                JSON.replace("}", ",\"referenceDate\":\"2021-01-01\"}"),
                JSON.replace("}", ",\"referenceDate\":\"2020-02-30\"}"),
                JSON.replace("}", ",\"id\":99}"),
                JSON.replace("}", ",\"id\":null}"),
                JSON.replace("}", ",\"verificationStatus\":\"VERIFIED\"}"),
                JSON.replace("}", ",\"verificationStatus\":\"DRAFT\"}"),
                JSON.replace("}", ",\"verificationStatus\":null}"),
                JSON.replace("}", ",\"createdAt\":\"2020-01-01T00:00:00Z\"}"),
                JSON.replace("}", ",\"updatedAt\":\"2020-01-01T00:00:00Z\"}"),
                JSON.replace("}", ",\"unexpected\":true}"))) {
            mvc.perform(request(method(operation), path(operation)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.path").value(path(operation)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET_ONE", "PUT", "DELETE"})
    @WithMockUser
    void malformedPathIdReturnsBadRequest(String operation) throws Exception {
        mvc.perform(request(method(operation), BASE + "/invalid").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    private String path(String operation) {
        return operation.equals("GET") || operation.equals("POST") ? BASE : BASE + "/1";
    }

    private HttpMethod method(String operation) {
        return HttpMethod.valueOf(operation.equals("GET_ONE") ? "GET" : operation);
    }
}
