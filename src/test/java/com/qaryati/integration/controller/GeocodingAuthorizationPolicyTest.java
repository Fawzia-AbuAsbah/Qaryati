package com.qaryati.integration.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeocodingAuthorizationPolicyTest {

    @Test
    void permitsEveryAuthenticatedProjectRoleToRead() {
        assertRoleExpression("search", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')");
        assertRoleExpression("reverse", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')");
    }

    private void assertRoleExpression(String method, String expression) {
        var annotated = Arrays.stream(GeocodingController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst().orElseThrow();
        var authorization = annotated.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals(expression, authorization.value());
    }
}
