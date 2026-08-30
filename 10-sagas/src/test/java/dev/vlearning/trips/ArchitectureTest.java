package dev.vlearning.trips;

import java.util.stream.Stream;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The distribution simulator. booking, flight, hotel, payment and the (future)
 * orchestrator behave like separately deployed processes: they may share the
 * message contract ({@code messages}) and the chaos toggles, but NEVER call
 * each other's beans — a convenience a network would not offer, so the build
 * refuses it too. If one of these rules fails on your saga code, you have
 * accidentally built a monolith with a broker on the side.
 */
@AnalyzeClasses(packages = "dev.vlearning.trips", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] SIMULATED_SERVICES = {"booking", "flight", "hotel", "payment", "orchestration"};

    @ArchTest
    static final ArchRule booking_talks_to_nobody_directly = isolated("booking");

    @ArchTest
    static final ArchRule flight_talks_to_nobody_directly = isolated("flight");

    @ArchTest
    static final ArchRule hotel_talks_to_nobody_directly = isolated("hotel");

    @ArchTest
    static final ArchRule payment_talks_to_nobody_directly = isolated("payment");

    @ArchTest
    static final ArchRule orchestrator_talks_to_nobody_directly = isolated("orchestration");

    @ArchTest
    static final ArchRule contract_knows_no_service = noClasses()
            .that().resideInAPackage("..trips.messages..")
            .should().dependOnClassesThat().resideInAnyPackage(packages(SIMULATED_SERVICES))
            .because("the message contract must not drag any service implementation along");

    @ArchTest
    static final ArchRule temporal_demo_is_self_contained = noClasses()
            .that().resideInAPackage("..trips.temporal..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(packages("booking", "flight", "hotel", "payment", "orchestration", "messages", "chaos"))
            .because("the Temporal taste (step 6) is a parallel universe, not part of the Kafka saga");

    private static ArchRule isolated(String service) {
        var others = Stream.of(SIMULATED_SERVICES)
                .filter(other -> !other.equals(service))
                .toArray(String[]::new);
        return noClasses()
                .that().resideInAPackage("..trips." + service + "..")
                .should().dependOnClassesThat().resideInAnyPackage(packages(others))
                .because("simulated services communicate ONLY via Kafka topics (see the messages package)");
    }

    private static String[] packages(String... services) {
        return Stream.of(services).map(service -> "..trips." + service + "..").toArray(String[]::new);
    }
}
