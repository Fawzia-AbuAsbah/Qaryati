package com.qaryati.community.controller;

import com.qaryati.community.dto.EconomicActivityRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EconomicActivityAuthorizationPolicyTest {

    @Test
    void declaresViewerReadEditorWriteAndAdminDeletePolicy() throws Exception {
        assertPolicy("create", "hasAnyRole('EDITOR', 'ADMIN')", EconomicActivityRequest.class);
        assertPolicy("getAll", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')", Long.class);
        assertPolicy("getById", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')", Long.class);
        assertPolicy("update", "hasAnyRole('EDITOR', 'ADMIN')", Long.class, EconomicActivityRequest.class);
        assertPolicy("delete", "hasRole('ADMIN')", Long.class);
    }

    private void assertPolicy(String methodName, String expected, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = EconomicActivityController.class.getMethod(methodName, parameterTypes);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " must declare authorization");
        assertEquals(expected, annotation.value());
    }
}
