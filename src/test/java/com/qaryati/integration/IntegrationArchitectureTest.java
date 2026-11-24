package com.qaryati.integration;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class IntegrationArchitectureTest {

    @Test
    void integrationProductionCodeDoesNotDependOnBusinessModules() {
        var integration = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.qaryati.integration");

        classes().that().resideInAPackage("com.qaryati.integration..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("com.qaryati.integration..", "tools.jackson..", "java..", "jakarta..", "org..")
                .check(integration);
    }
}
