package dev.vlearning.registration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.jmolecules.archunit.JMoleculesDddRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Checkpoint 5 — references across aggregates and contexts. Two rules:
 * aggregates point at other aggregates by identifier, never by object
 * reference (jMolecules ships this rule); and the enrollment context talks to
 * the catalog only through its published interface, never through
 * {@code catalog.internal}. Expect the second rule to be red when you enable
 * this — {@code EnrollmentService} still injects the catalog's repository.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5IdReferencesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.vlearning.registration");
    }

    @Test
    void aggregatesReferenceOtherAggregatesById() {
        JMoleculesDddRules.aggregateReferencesShouldBeViaIdOrAssociation().check(classes);
    }

    @Test
    void enrollmentNeverReachesIntoCatalogInternals() {
        noClasses()
                .that().resideInAPackage("..enrollment..")
                .should().dependOnClassesThat().resideInAPackage("..catalog.internal..")
                .because("a bounded context is reached through its published interface, "
                        + "not by grabbing its entities and repositories")
                .check(classes);
    }
}
