package dev.vlearning.library;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The architecture fitness function of this lesson. Spring Modulith derives
 * the module structure from the package layout (every direct subpackage of
 * dev.vlearning.library is a module) and verify() turns the boundary rules
 * into a plain JUnit failure.
 *
 * Checkpoint 1: enable both tests. verify() FAILS on the given code — that
 * failure report is the map for steps 2–4. It goes green at the end of step 4.
 */
class ModularityTests {

    @Test
    @Disabled("Checkpoint 1 — enable when you start step 1")
    void printModuleArrangement() {
        ApplicationModules.of(LibraryApplication.class).forEach(System.out::println);
    }

    @Test
    @Disabled("Checkpoint 1 — enable when you start step 1 (stays red until step 4 is done)")
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(LibraryApplication.class).verify();
    }
}
