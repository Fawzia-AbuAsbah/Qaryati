package com.qaryati.security.controller;

import com.qaryati.security.config.JwtAuthenticationConverterConfig;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.dto.LoginRequest;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.security.service.AuthenticationService;
import com.qaryati.security.service.DatabaseUserDetailsService;
import com.qaryati.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated MVC and bearer-converter contracts; all authentication and persistence dependencies are mocked. */
@WebMvcTest(controllers = LoginController.class, properties = "app.jwt.expiration-minutes=15")
@Import({SecurityConfig.class, JwtAuthenticationConverterConfig.class})
class LoginControllerTest {
    private static final String USERNAME = "Case.User_01";
    private static final String TOKEN = "synthetic-token";
    private static final String ME = "/api/v1/auth/me";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final LoginRequest LOGIN_REQUEST = new LoginRequest("login-user", "synthetic-input");
    private static final String LOGIN_JSON = """
            {"username":"login-user","password":"synthetic-input"}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @MockitoBean private AuthenticationService authenticationService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private DatabaseUserDetailsService users;
    @MockitoBean private LoginRateLimitService rateLimits;

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "EDITOR", "ADMIN"})
    void meReportsEachEffectiveDatabaseRoleInsteadOfConflictingJwtClaims(String role) throws Exception {
        decode(role.equals("ADMIN") ? List.of("VIEWER") : List.of("ADMIN"));
        databaseUser(List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        expectMe(List.of(role));
    }

    @Test
    void meSortsAndDeduplicatesMultipleEffectiveRoles() throws Exception {
        decode(List.of("UNRELATED"));
        databaseUser(List.of(new SimpleGrantedAuthority("ROLE_VIEWER"),
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_EDITOR"),
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_VIEWER")));

        expectMe(List.of("ADMIN", "EDITOR", "VIEWER"));
    }

    @Test
    void meKeepsEmptyEffectiveRolesEmptyDespiteAdministratorJwtClaims() throws Exception {
        decode(List.of("ADMIN"));
        databaseUser(List.of());

        expectMe(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_AUDITOR", "ADMIN", "VIEWER", "role_admin", "ROLE_admin",
            "SCOPE_ADMIN", "ROLE_ADMINISTRATOR", "ROLE_", "ROLE_ADMIN "})
    void meIgnoresAuthoritiesOutsideTheExactApplicationRoleAllowlist(String authority) throws Exception {
        decode(List.of("ADMIN"));
        databaseUser(List.of(new SimpleGrantedAuthority(authority)));

        expectMe(List.of());
    }

    @Test
    void meIgnoresComplexAuthoritiesWithNoStringRepresentation() throws Exception {
        decode(List.of("ADMIN"));
        var complex = mock(GrantedAuthority.class);
        when(complex.getAuthority()).thenReturn(null);
        databaseUser(List.of(complex, new SimpleGrantedAuthority("ROLE_VIEWER")));

        expectMe(List.of("VIEWER"));
    }

    @Test
    void anonymousMeIsUnauthorizedWithoutAuthenticationOrTokenServiceCalls() throws Exception {
        mvc.perform(get(ME)).andExpect(status().isUnauthorized());

        verifyNoInteractions(authenticationService, jwtService, decoder, users, rateLimits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "a.b.c"})
    void invalidBearerMeIsUnauthorizedBeforeDatabaseUserLookup(String token) throws Exception {
        when(decoder.decode(token)).thenThrow(new BadJwtException("Synthetic invalid token"));

        mvc.perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(decoder).decode(token);
        verifyNoMoreInteractions(decoder);
        verifyNoInteractions(authenticationService, jwtService, users, rateLimits);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrDisabledDatabaseUsersCannotUseMe(boolean disabled) throws Exception {
        decode(List.of("ADMIN"));
        if (disabled) {
            var user = mock(UserDetails.class);
            when(user.isEnabled()).thenReturn(false);
            when(users.loadUserByUsername(USERNAME)).thenReturn(user);
        } else {
            when(users.loadUserByUsername(USERNAME)).thenThrow(new UsernameNotFoundException("Synthetic missing user"));
        }

        mvc.perform(get(ME).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());

        verifyBearerLookups();
        verifyNoInteractions(authenticationService, jwtService, rateLimits);
    }

    @Test
    void loginKeepsItsExistingTokenResponseAndExpirationWithoutCreatingAJwtService() throws Exception {
        when(rateLimits.allowRequest(anyString())).thenReturn(true);
        var authentication = new UsernamePasswordAuthenticationToken("login-user", null,
                List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));
        when(authenticationService.authenticate(LOGIN_REQUEST)).thenReturn(authentication);
        when(jwtService.generateToken(authentication)).thenReturn("synthetic-issued-token");

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"accessToken":"synthetic-issued-token","tokenType":"Bearer","expiresIn":900}
                        """, JsonCompareMode.STRICT));

        verify(authenticationService).authenticate(LOGIN_REQUEST);
        verify(jwtService).generateToken(authentication);
        verify(rateLimits).allowRequest(anyString());
        verifyNoMoreInteractions(authenticationService, jwtService, rateLimits);
        verifyNoInteractions(decoder, users);
    }

    @Test
    void loginKeepsItsGenericAuthenticationFailureResponse() throws Exception {
        when(rateLimits.allowRequest(anyString())).thenReturn(true);
        when(authenticationService.authenticate(LOGIN_REQUEST))
                .thenThrow(new BadCredentialsException("Synthetic private rejection details"));

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Invalid credentials\"}", JsonCompareMode.STRICT));

        verify(authenticationService).authenticate(LOGIN_REQUEST);
        verify(rateLimits).allowRequest(anyString());
        verifyNoMoreInteractions(authenticationService, rateLimits);
        verifyNoInteractions(jwtService, decoder, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"username\":\" \",\"password\":\"synthetic-input\"}"})
    void invalidLoginBodiesAreRejectedBeforeAuthentication(String body) throws Exception {
        when(rateLimits.allowRequest(anyString())).thenReturn(true);

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Invalid request data\"}", JsonCompareMode.STRICT));

        verify(rateLimits).allowRequest(anyString());
        verifyNoMoreInteractions(rateLimits);
        verifyNoInteractions(authenticationService, jwtService, decoder, users);
    }

    @Test
    void sliceHasOnlyMockedServicesAndNoPersistenceOrMigrationInfrastructure() throws Exception {
        assertTrue(mockingDetails(authenticationService).isMock());
        assertTrue(mockingDetails(jwtService).isMock());
        assertTrue(mockingDetails(users).isMock());
        assertEquals(0, context.getBeanNamesForType(DataSource.class, false, false).length);
        assertEquals(0, context.getBeanNamesForType(jakarta.persistence.EntityManagerFactory.class, false, false).length);
        assertEquals(0, context.getBeanNamesForType(org.springframework.jdbc.core.JdbcTemplate.class, false, false).length);
        assertEquals(0, context.getBeanNamesForType(org.springframework.data.repository.Repository.class, false, false).length);
        assertEquals(0, context.getBeanNamesForType(org.flywaydb.core.Flyway.class, false, false).length);
        assertFalse(context.containsBean("flywayInitializer"));
        assertFalse(context.containsBean("liquibase"));
        try {
            var liquibase = Class.forName("liquibase.integration.spring.SpringLiquibase", false, getClass().getClassLoader());
            assertEquals(0, context.getBeanNamesForType(liquibase, false, false).length);
        } catch (ClassNotFoundException absentOptionalDependency) {
            // No compile-time Liquibase dependency is needed to inspect this slice.
        }
        verifyNoInteractions(authenticationService, jwtService, decoder, users, rateLimits);
    }

    private void decode(List<String> claimedRoles) {
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "RS256")
                .subject(USERNAME).claim("roles", claimedRoles).claim("preferred_username", "claimed-other-user").build());
    }

    private void databaseUser(List<GrantedAuthority> authorities) {
        var user = mock(UserDetails.class);
        when(user.isEnabled()).thenReturn(true);
        // Preserve duplicates: the standard User implementation would normalize its authority collection.
        doReturn(authorities).when(user).getAuthorities();
        when(users.loadUserByUsername(USERNAME)).thenReturn(user);
    }

    private void expectMe(List<String> expectedRoles) throws Exception {
        String roles = expectedRoles.stream().map(role -> "\"" + role + "\"").collect(Collectors.joining(","));
        var response = mvc.perform(get(ME).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"username\":\"" + USERNAME + "\",\"roles\":[" + roles + "]}",
                        JsonCompareMode.STRICT))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.username").value(USERNAME));
        for (String field : List.of("accessToken", "refreshToken", "token", "password", "passwordHash", "email",
                "credentials", "authorities", "claims")) {
            response.andExpect(jsonPath("$." + field).doesNotHaveJsonPath());
        }
        verifyBearerLookups();
        verifyNoInteractions(authenticationService, jwtService, rateLimits);
    }

    private void verifyBearerLookups() {
        verify(decoder).decode(TOKEN);
        verify(users).loadUserByUsername(USERNAME);
        verifyNoMoreInteractions(decoder, users);
    }
}
