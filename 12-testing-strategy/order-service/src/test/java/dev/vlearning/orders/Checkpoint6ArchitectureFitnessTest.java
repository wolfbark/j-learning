package dev.vlearning.orders;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Checkpoint 6 — fitness functions, consumer side. All of these pass on the given
 * code; the deliberately broken rule is in the payment-service. Run both.
 *
 * <p>The interesting rule is the first one: {@code pricing} is the package PIT
 * will attack in step 7, and it is testable in microseconds precisely because
 * nothing in it is allowed to know about HTTP, Spring MVC or the payment client.
 * The fitness function is what keeps it that way after the next feature request.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6ArchitectureFitnessTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.vlearning.orders");

    @Test
    void pricingIsPureDomainLogic() {
        noClasses().that().resideInAPackage("..pricing..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..order..", "..payments..")
                .because("a pricing rule you cannot test without a web layer is a pricing rule nobody tests")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void theWebLayerIsOnlyEnteredFromOutside() {
        noClasses().that().resideInAnyPackage("..order..", "..pricing..", "..payments..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .because("controllers are an entry point, not a utility")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void theOrderFlowTalksToPaymentsThroughThePortOnly() {
        noClasses().that().resideInAPackage("..order..")
                .should().dependOnClassesThat().haveSimpleName("PaymentClient")
                .orShould().dependOnClassesThat().haveSimpleName("HttpPaymentGateway")
                .because("the order flow depends on PaymentPort; the HTTP details are an adapter")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void restControllersAreNamedController() {
        classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .andShould().resideInAPackage("..api..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void thereAreNoPackageCycles() {
        slices().matching("dev.vlearning.orders.(*)..").should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }
}
