package com.qaryati.population;

import com.qaryati.QaryatiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class PopulationArchitectureTest {
    @Test void verifiesModulithAndPopulationDependencies() {
        ApplicationModules.of(QaryatiApplication.class).verify();
        classes().that().resideInAPackage("com.qaryati.population..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("com.qaryati.population..",
                        "com.qaryati.village.api..", "com.qaryati.dataquality.api..",
                        "com.qaryati.common.exception..", "com.qaryati.common.response..",
                        "java..", "jakarta..", "org..", "com.fasterxml.jackson..")
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.qaryati"));
    }
}
