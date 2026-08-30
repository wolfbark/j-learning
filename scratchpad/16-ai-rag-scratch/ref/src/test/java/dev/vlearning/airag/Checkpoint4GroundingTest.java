package dev.vlearning.airag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vlearning.airag.ask.RagService;
import dev.vlearning.airag.support.PgVectorTestBase;
import dev.vlearning.airag.support.TestModels;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Checkpoint 4 — grounding. The service must know the difference between "here is the answer" and
 * "this corpus does not contain the answer", and it must decide that <em>before</em> it spends a
 * token on generation.
 */
@SpringBootTest(properties = "rag.similarity-threshold=0.15")
@Import(TestModels.class)
class Checkpoint4GroundingTest extends PgVectorTestBase {

    @Autowired
    RagService ragService;

    @Test
    void aQuestionOutsideTheCorpusIsRefusedWithoutAskingTheModel() {
        this.ingestionService.ingestAll();

        var answer = this.ragService.ask(
                "What is the airspeed velocity of an unladen swallow in metres per second?");

        assertThat(answer.isRefusal()).as("answer was %s", answer).isTrue();
        assertThat(answer.citations()).as("a refusal must never carry a citation").isEmpty();
        assertThat(this.scriptedChatModel.promptCount())
                .as("the model must not be asked to generate prose it has no grounds for")
                .isZero();
    }

    @Test
    void aQuestionInsideTheCorpusStillGetsAnAnsweredWithCitations() {
        this.ingestionService.ingestAll();

        var answer = this.ragService.ask(
                "How do I publish events without a dual write, using an outbox and idempotent consumers?");

        assertThat(answer.isRefusal()).as("answer was %s", answer).isFalse();
        assertThat(answer.citations()).isNotEmpty();
        assertThat(answer.citations()).allSatisfy(citation ->
                assertThat(citation.sourceFile()).endsWith(".md"));
        assertThat(this.scriptedChatModel.promptCount()).isEqualTo(1);
    }

    /** An empty index is the same situation as an irrelevant one: refuse, do not improvise. */
    @Test
    void anEmptyIndexRefusesEverything() {
        var answer = this.ragService.ask("What is the transactional outbox pattern?");

        assertThat(answer.isRefusal()).isTrue();
        assertThat(this.scriptedChatModel.promptCount()).isZero();
    }
}
