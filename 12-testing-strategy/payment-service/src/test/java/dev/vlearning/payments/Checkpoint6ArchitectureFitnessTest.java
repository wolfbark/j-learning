package dev.vlearning.payments;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Checkpoint 6 — fitness functions. These are ordinary JUnit tests (plain
 * ArchUnit, no separate engine), so {@code @Disabled} behaves normally.
 *
 * <p>One of these rules fails on the given code. That is not a bug in the rule:
 * fix the code, not the test. If you ever find yourself relaxing a fitness
 * function to make it green, you have just deleted the only thing keeping the
 * architecture honest.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6ArchitectureFitnessTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.vlearning.payments");

    @Test
    void webMustNotReachIntoPersistenceDirectly() {
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAPackage("..persistence..")
                .because("controllers talk to the domain; the domain owns storage")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void theDomainMustNotDependOnTheWeb() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..web..")
                .because("the domain must be usable without HTTP — that is what makes it unit-testable")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void persistenceMustNotDependOnTheWeb() {
        noClasses().that().resideInAPackage("..persistence..")
                .should().dependOnClassesThat().resideInAPackage("..web..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void restControllersAreNamedController() {
        classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void repositoryImplementationsLiveInPersistence() {
        classes().that().areAnnotatedWith(org.springframework.stereotype.Repository.class)
                .should().resideInAPackage("..persistence..")
                .andShould().haveSimpleNameEndingWith("Repository")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void thereAreNoPackageCycles() {
        slices().matching("dev.vlearning.payments.(*)..").should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }
}
