package com.qaryati.community.controller;

import com.qaryati.community.dto.CommunityFacilityRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommunityFacilityAuthorizationPolicyTest {

    @Test
    void declaresViewerReadEditorWriteAndAdminDeletePolicy() throws Exception {
        assertPolicy("create", "hasAnyRole('EDITOR', 'ADMIN')", CommunityFacilityRequest.class);
        assertPolicy("getAll", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')", Long.class);
        assertPolicy("getById", "hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')", Long.class);
        assertPolicy("update", "hasAnyRole('EDITOR', 'ADMIN')", Long.class, CommunityFacilityRequest.class);
        assertPolicy("delete", "hasRole('ADMIN')", Long.class);
    }

    private void assertPolicy(String methodName, String expected, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = CommunityFacilityController.class.getMethod(methodName, parameterTypes);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " must declare authorization");
        assertEquals(expected, annotation.value());
    }
}
