package dev.vlearning.tasks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Architecture-as-tests: turns "slices must not know about each other" from a code
 * review opinion into a build failure. Enable after step 4, when everything lives
 * under features/. Then deliberately break it (import one slice's class from another)
 * and read the failure message — that message is the whole point of this lesson.
 */
@Disabled("Checkpoint 5 — enable after step 4")
class Checkpoint5SliceIndependenceTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.vlearning.tasks");
    }

    @Test
    void featureSlicesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching("..features.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    @Test
    void featureSlicesDoNotDependOnEachOther() {
        SlicesRuleDefinition.slices()
                .matching("..features.(*)..")
                .should().notDependOnEachOther()
                .check(classes);
    }
}
