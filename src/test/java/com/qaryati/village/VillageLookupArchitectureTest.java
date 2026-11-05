package com.qaryati.village;

import com.qaryati.QaryatiApplication;
import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.village.api.VillageLookupResult;
import com.qaryati.village.api.VillageLookupStatus;
import com.qaryati.village.dto.VillageResponse;
import com.qaryati.village.entity.Village;
import com.qaryati.village.entity.VillageStatus;
import com.qaryati.village.repository.VillageRepository;
import com.qaryati.village.service.VillageLookupService;
import com.qaryati.village.service.VillageService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

class VillageLookupArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.qaryati");

    @Test
    void verifiesModuleBoundariesAndExposesOnlyLookupContract() {
        var modules = ApplicationModules.of(QaryatiApplication.class);
        modules.verify();

        var village = modules.getModuleByName("village").orElseThrow();
        var lookup = village.getNamedInterfaces().getByName("lookup").orElseThrow();
        var expected = Set.of(VillageLookupApi.class.getName(), VillageLookupResult.class.getName(),
                VillageLookupStatus.class.getName());

        assertFalse(village.isOpen());
        assertEquals(expected, lookup.asJavaClasses().map(type -> type.getName()).collect(Collectors.toSet()));
        assertEquals(expected, village.getNamedInterfaces().stream()
                .flatMap(namedInterface -> namedInterface.asJavaClasses())
                .map(type -> type.getName()).collect(Collectors.toSet()));
        for (var internal : List.of(Village.class, VillageStatus.class, VillageRepository.class,
                VillageService.class, VillageLookupService.class, VillageResponse.class)) {
            assertFalse(village.isExposed(internal), internal.getName());
        }
    }

    @Test
    void publicContractContainsOnlyOptionalLookupResultAndImmutableState() throws Exception {
        assertTrue(VillageLookupApi.class.isInterface());
        assertEquals(1, VillageLookupApi.class.getDeclaredMethods().length);
        var method = VillageLookupApi.class.getMethod("findById", Long.class);
        var returnType = assertInstanceOf(ParameterizedType.class, method.getGenericReturnType());
        assertEquals(Optional.class, returnType.getRawType());
        assertArrayEquals(new Class<?>[]{VillageLookupResult.class}, returnType.getActualTypeArguments());

        assertTrue(VillageLookupResult.class.isRecord());
        var components = VillageLookupResult.class.getRecordComponents();
        assertEquals(List.of("villageId", "status", "archived"),
                Arrays.stream(components).map(RecordComponent::getName).toList());
        assertEquals(List.of(Long.class, VillageLookupStatus.class, boolean.class),
                Arrays.stream(components).map(RecordComponent::getType).toList());
        assertEquals(List.of("DRAFT", "UNDER_REVIEW", "VERIFIED", "REJECTED"),
                Arrays.stream(VillageLookupStatus.values()).map(Enum::name).toList());
    }

    @Test
    void publicApiHasNoDependenciesOnInternalVillageTypes() {
        classes().that().resideInAPackage("com.qaryati.village.api..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("com.qaryati.village.api..", "java..", "org.springframework.modulith..")
                .check(PRODUCTION);
    }

    @Test
    void otherModulesCannotDependOnVillageInternals() {
        noClasses().that().resideOutsideOfPackage("com.qaryati.village..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.qaryati.village.entity..", "com.qaryati.village.repository..",
                        "com.qaryati.village.service..", "com.qaryati.village.dto..",
                        "com.qaryati.village.controller..", "com.qaryati.village.mapper..")
                .check(PRODUCTION);
    }

    @Test
    void villageHasNoReverseDependencyOnPopulationOrHeritage() {
        noClasses().that().resideInAPackage("com.qaryati.village..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.qaryati.population..", "com.qaryati.heritage..")
                .check(PRODUCTION);
    }
}
