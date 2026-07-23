package com.example.ecommerce;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@AnalyzeClasses(
    packages = "com.example.ecommerce",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule web_layers_must_not_access_repositories =
        noClasses()
            .that().resideInAnyPackage("..controller..", "..jersey..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..")
            .because("HTTP adapters delegate to services, not repositories");

    @ArchTest
    static final ArchRule services_must_not_access_web_layers =
        noClasses()
            .that().resideInAnyPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..jersey..")
            .because("Business logic must not depend on HTTP adapters");

    @ArchTest
    static final ArchRule repositories_must_not_access_services =
        noClasses()
            .that().resideInAnyPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..")
            .because("Persistence layer must stay below application services");

    @ArchTest
    static final ArchRule repositories_must_not_access_web_layers =
        noClasses()
            .that().resideInAnyPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..jersey..")
            .because("Persistence layer must not depend on HTTP adapters");

    @ArchTest
    static final ArchRule no_field_injection =
        noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("Use constructor injection for required dependencies");
}
