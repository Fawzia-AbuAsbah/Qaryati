package com.qaryati.agriculture.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VillageAgricultureAuthorizationPolicyTest {

    @Test
    void declaresViewerReadEditorWriteAndAdminDeletePolicy() throws Exception {
        assertPolicy("create", "hasAnyRole('EDITOR', 'ADMIN')",
                com.qaryati.agriculture.dto.VillageAgricultureRequest.class);
        assertPolicy("getById", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')", Long.class);
        assertPolicy("getByVillage", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')", Long.class);
        assertPolicy("getByProduct", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')", Long.class);
        assertPolicy("update", "hasAnyRole('EDITOR', 'ADMIN')", Long.class,
                com.qaryati.agriculture.dto.VillageAgricultureRequest.class);
        assertPolicy("delete", "hasRole('ADMIN')", Long.class);
    }

    private void assertPolicy(String methodName, String expected, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = VillageAgricultureController.class.getMethod(methodName, parameterTypes);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " must declare an authorization policy");
        assertEquals(expected, annotation.value());
    }
}
