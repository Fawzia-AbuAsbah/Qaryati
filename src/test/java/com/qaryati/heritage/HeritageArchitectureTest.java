package com.qaryati.heritage;

import com.qaryati.QaryatiApplication;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class HeritageArchitectureTest {
    @Test void heritageProductionCodeHasNoOtherModuleDependencies() {
        var heritage = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.qaryati.heritage");
        classes().that().resideInAPackage("com.qaryati.heritage..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("com.qaryati.heritage..", "java..", "jakarta..", "org..")
                .check(heritage);
    }

    @Test void verifiesModuleBoundariesWithoutApplicationContext() {
        ApplicationModules.of(QaryatiApplication.class).verify();
    }
}
