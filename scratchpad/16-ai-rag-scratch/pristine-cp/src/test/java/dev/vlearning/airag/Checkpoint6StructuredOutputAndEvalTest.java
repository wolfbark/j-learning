package dev.vlearning.airag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vlearning.airag.ask.RagService;
import dev.vlearning.airag.eval.RagEvaluator;
import dev.vlearning.airag.support.GoldenSet;
import dev.vlearning.airag.support.PgVectorTestBase;
import dev.vlearning.airag.support.TestModels;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Checkpoint 6 — a typed answer and a number that says whether any of this works.
 */
@SpringBootTest(properties = "rag.similarity-threshold=0.15")
@Import(TestModels.class)
class Checkpoint6StructuredOutputAndEvalTest extends PgVectorTestBase {

    /** The floor. Raise it when you improve retrieval; never lower it to make a build green. */
    private static final double MINIMUM_SCORE = 0.80;

    @Autowired
    RagService ragService;

    @Autowired
    RagEvaluator evaluator;

    @Test
    void theModelFillsTheAnswerRecordItself() {
        this.ingestionService.ingestAll();

        var answer = this.ragService.ask("What does the transactional outbox do about idempotent consumers?");

        assertThat(answer.answer()).isNotBlank();
        assertThat(answer.citations()).isNotEmpty();
        assertThat(answer.confidence()).isIn("high", "low");

        // Structured output is not string parsing: Spring AI appends a JSON Schema to the prompt
        // and binds the reply to the record. If the schema is missing, you assembled it by hand.
        assertThat(this.scriptedChatModel.prompts()).isNotEmpty();
        assertThat(this.scriptedChatModel.prompts().getLast().getContents())
                .as("the prompt must carry the structured-output schema")
                .contains("$schema");
    }

    @Test
    void theEvalHarnessScoresTheGoldenSetAboveTheFloor() {
        this.ingestionService.ingestAll();

        var report = this.evaluator.evaluate(GoldenSet.CASES);

        assertThat(report.total()).isEqualTo(GoldenSet.CASES.size());
        assertThat(report.score())
                .as("eval score %s; failures: %s", report.summary(), report.failures())
                .isGreaterThanOrEqualTo(MINIMUM_SCORE);
    }

    /** A harness that cannot fail is not a harness. */
    @Test
    void theHarnessFailsCasesThatDoNotRetrieveTheExpectedCitation() {
        this.ingestionService.ingestAll();

        var report = this.evaluator.evaluate(java.util.List.of(new dev.vlearning.airag.eval.EvalCase(
                "What is the airspeed velocity of an unladen swallow?", "ornithology.md", "Swallows")));

        assertThat(report.passed()).isZero();
        assertThat(report.failures()).hasSize(1);
    }
}
