package dev.vlearning.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Generates C4/PlantUML component diagrams and a per-module Application
 * Module Canvas into target/spring-modulith-docs — architecture documentation
 * derived from the code, so it cannot go stale.
 */
@Disabled("Checkpoint 7 — enable when you start step 7")
class Checkpoint7DocumentationTest {

    @Test
    void generatesModuleDocumentation() throws IOException {
        var modules = ApplicationModules.of(LibraryApplication.class);

        new Documenter(modules).writeDocumentation();

        Path docs = Path.of("target", "spring-modulith-docs");
        assertThat(docs).isDirectory();
        try (Stream<Path> files = Files.list(docs)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .anyMatch(name -> name.endsWith(".puml"));
        }
    }
}
