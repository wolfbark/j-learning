package dev.vlearning.airag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vlearning.airag.ask.RagService;
import dev.vlearning.airag.support.PgVectorTestBase;
import dev.vlearning.airag.support.TestModels;
import dev.vlearning.airag.tools.LessonIndex;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Checkpoint 5 — tool calling. The model asks for {@code whichLessonCovers}, Spring AI runs your
 * Java method, feeds the result back, and the model answers with it. The fake model is scripted to
 * request the call, which is exactly what a real one would send over the wire.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
@SpringBootTest
@Import(TestModels.class)
class Checkpoint5ToolCallingTest extends PgVectorTestBase {

    @Autowired
    RagService ragService;

    @Autowired
    LessonIndex lessonIndex;

    @Test
    void theModelInvokesTheLessonLookupToolAndUsesItsResult() {
        this.ingestionService.ingestAll();
        this.lessonIndex.resetInvocationCount();

        this.scriptedChatModel
                .scriptToolCall("whichLessonCovers", "{\"topic\": \"virtual threads\"}")
                .scriptQuotingToolResults("The lesson index says: ");

        var answer = this.ragService.ask("Which lesson covers virtual threads?");

        assertThat(this.lessonIndex.invocationCount())
                .as("the @Tool method must actually run")
                .isEqualTo(1);
        assertThat(answer.answer())
                .as("the tool result must reach the answer")
                .contains("14-virtual-threads");
        assertThat(this.scriptedChatModel.promptCount())
                .as("tool calling is two model round trips: request the call, then answer with it")
                .isEqualTo(2);
    }

    /** The tool is offered on every request; a model that does not want it simply answers. */
    @Test
    void anOrdinaryQuestionNeedsNoToolCall() {
        this.ingestionService.ingestAll();
        this.lessonIndex.resetInvocationCount();

        this.ragService.ask("What is the transactional outbox pattern?");

        assertThat(this.lessonIndex.invocationCount()).isZero();
        assertThat(this.scriptedChatModel.promptCount()).isEqualTo(1);
    }

    @Test
    void theCuratedIndexKnowsAllSixteenProjects() {
        assertThat(LessonIndex.lessons()).hasSize(16);
        assertThat(this.lessonIndex.whichLessonCovers("pgvector")).contains("16-ai-backend");
        assertThat(this.lessonIndex.whichLessonCovers("quantum teleportation"))
                .contains("No lesson");
    }
}
