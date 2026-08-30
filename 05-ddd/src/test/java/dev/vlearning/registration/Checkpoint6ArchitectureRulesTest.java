package dev.vlearning.registration;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.jmolecules.archunit.JMoleculesDddRules;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Checkpoint 6 — the metamodel and the boundaries, enforced. If steps 2-5 are
 * done, everything here is green on enable. Then comes the important part of
 * the exercise: break it on purpose (put a {@code Course} field back into
 * {@code Enrollment}, import something from {@code catalog.internal}) and
 * read the failure messages. Those messages, in CI, are what keep the model
 * alive after everyone stops paying attention.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6ArchitectureRulesTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.vlearning.registration");
    }

    @Test
    void theWholeJMoleculesDddMetamodelHolds() {
        JMoleculesDddRules.all().check(importedClasses);
    }

    @Test
    void enrollmentStaysOutOfCatalogInternals() {
        noClasses()
                .that().resideInAPackage("..enrollment..")
                .should().dependOnClassesThat().resideInAPackage("..catalog.internal..")
                .check(importedClasses);
    }

    @Test
    void catalogInternalsAreOnlyTouchedByTheCatalogItself() {
        classes()
                .that().resideInAPackage("..catalog.internal..")
                .should().onlyHaveDependentClassesThat().resideInAPackage("..catalog..")
                .check(importedClasses);
    }

    @Test
    void theUpstreamCatalogKnowsNothingAboutItsConsumers() {
        noClasses()
                .that().resideInAPackage("..catalog..")
                .should().dependOnClassesThat().resideInAnyPackage("..enrollment..", "..notifications..")
                .check(importedClasses);
    }

    @Test
    void enrollmentDoesNotKnowWhoListens() {
        noClasses()
                .that().resideInAPackage("..enrollment..")
                .should().dependOnClassesThat().resideInAPackage("..notifications..")
                .because("downstream conformists depend on upstream events, never the other way")
                .check(importedClasses);
    }

    @Test
    void theConformistConsumesPublishedFactsNotServices() {
        noClasses()
                .that().resideInAPackage("..notifications..")
                .should().dependOnClassesThat(
                        resideInAPackage("..enrollment..")
                                .and(not(annotatedWith(DomainEvent.class)))
                                .and(not(annotatedWith(ValueObject.class))))
                .because("a conformist reacts to the upstream's published facts and their value "
                        + "types; calling its services makes it a hidden partnership")
                .check(importedClasses);
    }
}
