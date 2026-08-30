package dev.vlearning.lending;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Checkpoint 1 — the minimal, legitimate form of CQRS: two code paths, one
 * database. Commands live in {@code dev.vlearning.lending.commands}, queries
 * in {@code dev.vlearning.lending.queries}, and neither knows the other
 * exists. No new infrastructure, no new tables — just a seam.
 *
 * Passing this checkpoint also requires LendingApiBehaviorTest to stay green:
 * the split must not change behavior.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
class Checkpoint1CommandQuerySplitTest {

    private JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("dev.vlearning.lending");
    }

    @Test
    void commandsMustNotDependOnQueries() {
        noClasses()
                .that().resideInAPackage("..lending.commands..")
                .should().dependOnClassesThat().resideInAPackage("..lending.queries..")
                .allowEmptyShould(true)
                .check(productionClasses());
    }

    @Test
    void queriesMustNotDependOnCommands() {
        noClasses()
                .that().resideInAPackage("..lending.queries..")
                .should().dependOnClassesThat().resideInAPackage("..lending.commands..")
                .allowEmptyShould(true)
                .check(productionClasses());
    }

    @Test
    void theDoEverythingServiceIsGone() {
        noClasses()
                .should().haveSimpleName("LibraryService")
                .because("the point of step 1 is to dissolve the service that serves both sides")
                .allowEmptyShould(true)
                .check(productionClasses());
    }
}
