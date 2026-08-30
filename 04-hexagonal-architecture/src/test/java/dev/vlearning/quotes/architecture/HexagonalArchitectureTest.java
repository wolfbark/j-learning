package dev.vlearning.quotes.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/**
 * Checkpoint 6. Architecture as executable tests: these rules only pass once
 * the quote feature is shaped as domain / application / adapter packages
 * (step 4 done). Written as plain JUnit tests (rather than ArchUnit's own
 * {@code @AnalyzeClasses} runner) so the checkpoint convention — remove
 * {@code @Disabled}, make it pass — stays the same as everywhere else.
 *
 * The ratestub package is deliberately ignored by every rule: it plays the
 * REMOTE system, it is not part of this application's architecture. The same
 * goes for a plain-CRUD products package, if you create one in step 7.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class HexagonalArchitectureTest {

    static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.vlearning.quotes");
    }

    @Test
    void domainMustNotTouchAnyFramework() {
        noClasses()
                .that().resideInAPackage("..quotes.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..")
                .because("the domain must compile, run and be tested with the JDK alone")
                .check(productionClasses);
    }

    @Test
    void applicationMustNotTouchAdaptersOrTheirTechnology() {
        noClasses()
                .that().resideInAPackage("..quotes.application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..quotes.adapter..",
                        "org.springframework.web..",
                        "org.springframework.data..",
                        "jakarta.persistence..")
                .because("the application orchestrates ports; HTTP and JPA are adapter business")
                .check(productionClasses);
    }

    @Test
    void onlyAdaptersMayDependOnAdapters() {
        noClasses()
                .that().resideOutsideOfPackage("..quotes.adapter..")
                .should().dependOnClassesThat()
                .resideInAPackage("..quotes.adapter..")
                .because("port implementations are interchangeable details; "
                        + "if anything outside the adapters referenced one, swapping it would break callers")
                .check(productionClasses);
    }

    /**
     * The same intent, expressed with ArchUnit's built-in onion vocabulary.
     * Our flat domain package is declared as BOTH the domain-model and the
     * domain-service ring — the mapping friction between hexagonal packages
     * and onion rings is part of the lesson (see step 7).
     */
    @Test
    void theOnionRuleAgreesWithOurHandwrittenRules() {
        onionArchitecture()
                .domainModels("..quotes.domain..")
                .domainServices("..quotes.domain..")
                .applicationServices("..quotes.application..")
                .adapter("web", "..quotes.adapter.in.web..")
                .adapter("persistence", "..quotes.adapter.out.persistence..")
                .adapter("rates", "..quotes.adapter.out.rates..")
                .check(productionClasses);
    }
}
