package com.qaryati.community;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class CommunityArchitectureTest {

    @Test
    void communityProductionCodeUsesOnlyItsOwnAndVillagePublicTypes() {
        var community = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.qaryati.community");

        classes().that().resideInAPackage("com.qaryati.community..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("com.qaryati.community..", "com.qaryati.village.api..",
                        "java..", "jakarta..", "org..")
                .check(community);
    }
}
