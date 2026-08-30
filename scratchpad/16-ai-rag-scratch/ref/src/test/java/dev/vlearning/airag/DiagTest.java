package dev.vlearning.airag;

import dev.vlearning.airag.ask.RagService;
import dev.vlearning.airag.support.GoldenSet;
import dev.vlearning.airag.support.PgVectorTestBase;
import dev.vlearning.airag.support.TestModels;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = { "rag.top-k=3", "rag.similarity-threshold=0.0" })
@Import(TestModels.class)
class DiagTest extends PgVectorTestBase {

    @Autowired
    RagService ragService;

    @Test
    void diag() {
        ingestionService.ingestAll();
        System.out.println("DIAG chunkcount=" + storedVectorCount());
        GoldenSet.CASES.forEach(c -> {
            System.out.println("DIAG Q: " + c.question() + "  [want " + c.expectedSource() + " / " + c.expectedHeading() + "]");
            ragService.retrieve(c.question()).forEach(p -> System.out.printf("DIAG    %.4f  %s | %s%n",
                    p.score(), p.sourceFile(), p.heading()));
        });
        for (String q : new String[] {
                "What is the airspeed velocity of an unladen swallow in metres per second?",
                "What is my grandmother's recipe for lemon drizzle cake?",
                "How do I replace the timing belt on a 1994 Volvo 940?" }) {
            System.out.println("DIAG OFF-CORPUS: " + q);
            ragService.retrieve(q).forEach(p -> System.out.printf("DIAG    %.4f  %s | %s%n",
                    p.score(), p.sourceFile(), p.heading()));
        }
    }
}
