package com.qaryati.village.controller;
import com.qaryati.common.exception.*;
import com.qaryati.security.config.*;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.security.service.DatabaseUserDetailsService;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.VillageNameType;
import com.qaryati.village.service.VillageNameService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VillageNameController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverterConfig.class, GlobalExceptionHandler.class})
class VillageNameControllerTest {
    static final String BASE = "/api/v1/villages/1/names";
    static final String JSON = "{\"name\":\" Name \",\"languageCode\":\" EN \",\"nameType\":\"FORMER\",\"notes\":null}";
    static final VillageNameRequest REQUEST = new VillageNameRequest(" Name ", " EN ", VillageNameType.FORMER,null);
    static final VillageNameResponse RESPONSE = new VillageNameResponse(2L,1L," Name "," EN ",VillageNameType.FORMER,null);
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @MockitoBean VillageNameService service;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean DatabaseUserDetailsService users;
    @MockitoBean LoginRateLimitService limits;

    static Stream<Arguments> roles() {
        return Stream.of("VIEWER","EDITOR","ADMIN","NONE","UNRELATED")
                .flatMap(role -> Stream.of("POST","LIST","ID","PUT","DELETE").map(op -> Arguments.of(role,op)));
    }
    @ParameterizedTest @MethodSource("roles")
    void productionSecurityMatrixAndContract(String role, String op) throws Exception {
        authenticate(role);
        boolean allowed = role.equals("ADMIN") || role.equals("EDITOR") && !op.equals("DELETE")
                || role.equals("VIEWER") && List.of("LIST","ID").contains(op);
        if (allowed) stub(op);
        var result = mvc.perform(endpoint(op).header("Authorization","Bearer token"));
        if (!allowed) { result.andExpect(status().isForbidden()); verifyNoInteractions(service); return; }
        result.andExpect(status().is(op.equals("POST") ? 201 : op.equals("DELETE") ? 204 : 200));
        if (op.equals("DELETE")) result.andExpect(content().string(""));
        else result.andExpect(content().json(op.equals("LIST") ? "[" + responseJson() + "]" : responseJson(),
                org.springframework.test.json.JsonCompareMode.STRICT));
        switch(op) {
            case "POST" -> verify(service).create(1L,REQUEST);
            case "PUT" -> verify(service).update(1L,2L,REQUEST);
            case "ID" -> verify(service).getById(1L,2L);
            case "LIST" -> verify(service).getAll(1L);
            default -> verify(service).delete(1L,2L);
        }
        verifyNoMoreInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"POST","LIST","ID","PUT","DELETE"})
    void anonymousDenied(String op) throws Exception {
        mvc.perform(endpoint(op)).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void invalidBearerDenied() throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("invalid"));
        mvc.perform(endpoint("ID").header("Authorization","Bearer bad")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,users);
    }
    static Stream<Arguments> invalidBodies() {
        return Stream.of("POST","PUT").flatMap(op -> Stream.of(
                "{}", "null", "{", JSON.replace(" Name "," "),
                JSON.replace(" EN ",""), JSON.replace("FORMER","former"), JSON.replace("FORMER","INVALID"),
                JSON.replace("\"FORMER\"","null"), JSON.replace("\"FORMER\"","0"), JSON.replace("\"FORMER\"","\"0\""), JSON.replace(" Name ","x".repeat(201)),
                JSON.replace(" EN ","l".repeat(13)),
                JSON.substring(0,JSON.length()-1)+",\"id\":null}",
                JSON.substring(0,JSON.length()-1)+",\"villageId\":9}",
                JSON.substring(0,JSON.length()-1)+",\"unknown\":1}"
        ).map(body -> Arguments.of(op,body)));
    }
    @ParameterizedTest @MethodSource("invalidBodies")
    void invalidRequestsRejectedBeforeService(String op,String body) throws Exception {
        authenticate("ADMIN");
        mvc.perform(endpoint(op).content(body).header("Authorization","Bearer token")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @EnumSource(VillageNameType.class)
    void everyTypeAcceptedAndOmittedNotesAreNull(VillageNameType type) throws Exception {
        authenticate("EDITOR");
        var expected = new VillageNameRequest(" Name "," EN ",type,null);
        when(service.create(1L,expected)).thenReturn(RESPONSE);
        mvc.perform(endpoint("POST").content("{\"name\":\" Name \",\"languageCode\":\" EN \",\"nameType\":\""+type+"\"}")
                .header("Authorization","Bearer token")).andExpect(status().isCreated());
        verify(service).create(1L,expected);
    }
    @ParameterizedTest @ValueSource(strings = {"/api/v1/villages/abc/names","/api/v1/villages/9223372036854775808/names",
            "/api/v1/villages/1/names/abc"})
    void malformedIdsRejected(String path) throws Exception {
        authenticate("VIEWER");
        mvc.perform(request(HttpMethod.GET,path).header("Authorization","Bearer token")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void businessErrorsUseExistingHandler() throws Exception {
        authenticate("ADMIN");
        for (int code : List.of(400,404,409)) {
            doThrow(new ResponseStatusException(HttpStatus.valueOf(code),"Safe message")).when(service).getById(1L,2L);
            mvc.perform(endpoint("ID").header("Authorization","Bearer token"))
                    .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code))
                    .andExpect(jsonPath("$.message").value("Safe message"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty()).andExpect(jsonPath("$.path").value(BASE+"/2"));
        }
        when(service.create(1L,REQUEST)).thenThrow(new DuplicateResourceException("Duplicate"));
        mvc.perform(endpoint("POST").header("Authorization","Bearer token")).andExpect(status().isConflict());
        doThrow(new ResourceNotFoundException("VillageName",2L)).when(service).delete(1L,2L);
        mvc.perform(endpoint("DELETE").header("Authorization","Bearer token")).andExpect(status().isNotFound());
    }
    @Test void emptyListAndNoPersistenceInfrastructure() throws Exception {
        authenticate("VIEWER"); when(service.getAll(1L)).thenReturn(List.of());
        mvc.perform(endpoint("LIST").header("Authorization","Bearer token")).andExpect(content().json("[]"));
        assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
        assertTrue(context.getBeansOfType(org.flywaydb.core.Flyway.class).isEmpty());
    }
    private String responseJson() { return "{\"id\":2,\"villageId\":1,\"name\":\" Name \",\"languageCode\":\" EN \",\"nameType\":\"FORMER\",\"notes\":null}"; }
    private void authenticate(String role) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg","RS256").subject("tester").build());
        when(users.loadUserByUsername("tester")).thenReturn(User.withUsername("tester").password("unused")
                .roles(role.equals("NONE") ? new String[0] : new String[]{role}).build());
    }
    private MockHttpServletRequestBuilder endpoint(String op) {
        var builder = request(HttpMethod.valueOf(List.of("POST","PUT","DELETE").contains(op) ? op : "GET"),
                BASE + (List.of("ID","PUT","DELETE").contains(op) ? "/2" : ""));
        if (List.of("POST","PUT").contains(op)) builder.contentType(MediaType.APPLICATION_JSON).content(JSON);
        return builder;
    }
    private void stub(String op) {
        switch(op) {
            case "POST" -> when(service.create(1L,REQUEST)).thenReturn(RESPONSE);
            case "PUT" -> when(service.update(1L,2L,REQUEST)).thenReturn(RESPONSE);
            case "ID" -> when(service.getById(1L,2L)).thenReturn(RESPONSE);
            case "LIST" -> when(service.getAll(1L)).thenReturn(List.of(RESPONSE));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"POST","PUT","DELETE"})
    void lifecycleConflictsReturn409(String op) throws Exception {
        authenticate("ADMIN");
        var failure = new ResponseStatusException(HttpStatus.CONFLICT,"Names require an unarchived DRAFT village");
        switch(op) {
            case "POST" -> doThrow(failure).when(service).create(1L,REQUEST);
            case "PUT" -> doThrow(failure).when(service).update(1L,2L,REQUEST);
            default -> doThrow(failure).when(service).delete(1L,2L);
        }
        mvc.perform(endpoint(op).header("Authorization","Bearer token")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
    @ParameterizedTest @ValueSource(strings = {"LIST","ID","POST","PUT","DELETE"})
    void serviceRejectsNonpositivePathIds(String op) throws Exception {
        authenticate("ADMIN");
        var failure = new ResponseStatusException(HttpStatus.BAD_REQUEST,"IDs must be positive");
        switch(op) {
            case "LIST" -> doThrow(failure).when(service).getAll(0L);
            case "ID" -> doThrow(failure).when(service).getById(0L,2L);
            case "POST" -> doThrow(failure).when(service).create(0L,REQUEST);
            case "PUT" -> doThrow(failure).when(service).update(0L,2L,REQUEST);
            default -> doThrow(failure).when(service).delete(0L,2L);
        }
        String path = "/api/v1/villages/0/names" + (List.of("ID","PUT","DELETE").contains(op) ? "/2" : "");
        var builder = request(HttpMethod.valueOf(List.of("POST","PUT","DELETE").contains(op) ? op : "GET"),path);
        if (List.of("POST","PUT").contains(op)) builder.contentType(MediaType.APPLICATION_JSON).content(JSON);
        mvc.perform(builder.header("Authorization","Bearer token")).andExpect(status().isBadRequest());
    }
}