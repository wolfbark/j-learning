package dev.vlearning.airag;

import java.util.List;
import dev.vlearning.airag.ingest.CorpusLoader;
import org.junit.jupiter.api.Test;

class RealCorpusTest {
    @Test
    void loadsTheActualRepository() {
        var loader = new CorpusLoader(new RagProperties(
                List.of("file:../docs/research/*.md", "file:../*/README.md"), 4, 0.0, 800, 40));
        var docs = loader.load();
        System.out.println("REAL count=" + docs.size());
        docs.forEach(d -> System.out.println("REAL  " + d.name() + " " + d.markdown().length()));
    }
}
