package com.qaryati;

import com.qaryati.security.api.UserLookup;
import com.qaryati.security.api.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ModulithArchitectureTest {
    @Test
    void verifiesBoundariesAndExposesOnlySecurityLookupApi() {
        var modules = ApplicationModules.of(QaryatiApplication.class);
        modules.verify();

        var security = modules.getModuleByName("security").orElseThrow();
        assertFalse(security.isOpen());
        assertEquals(Set.of(UserLookup.class.getName(), UserSummary.class.getName()),
                security.getNamedInterfaces().stream()
                        .flatMap(namedInterface -> namedInterface.asJavaClasses())
                        .map(type -> type.getName())
                        .collect(Collectors.toSet()));
        assertTrue(security.getNamedInterfaces().getByName("users").isPresent());
    }
}
