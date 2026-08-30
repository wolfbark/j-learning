package dev.vlearning.airag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.vlearning.airag.model.HashingEmbeddingModel;
import org.junit.jupiter.api.Test;

/**
 * ENABLED. Step 1's worked example: what an embedding is, and what "similar" means numerically.
 * No Spring, no container, no model — just arithmetic you can do on paper.
 */
class EmbeddingsAndSimilarityTest {

    /**
     * The cosine similarity of two vectors is their dot product over the product of their lengths.
     * Do it by hand once and the vector store stops being magic.
     */
    @Test
    void cosineSimilarityByHand() {
        float[] a = { 1, 0, 1 };
        float[] b = { 1, 1, 0 };

        // dot(a,b) = 1*1 + 0*1 + 1*0 = 1 ; |a| = |b| = sqrt(2) ; 1 / 2 = 0.5
        assertThat(HashingEmbeddingModel.cosine(a, b)).isCloseTo(0.5, within(1e-9));

        assertThat(HashingEmbeddingModel.cosine(a, a)).isCloseTo(1.0, within(1e-9));
        assertThat(HashingEmbeddingModel.cosine(a, new float[] { 0, 1, 0 })).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void tokenisationKeepsTheWordsThatCarryMeaning() {
        assertThat(HashingEmbeddingModel.tokenize("How does the Transactional Outbox avoid a dual write?"))
                .containsExactly("transactional", "outbox", "avoid", "dual", "write");
    }

    @Test
    void aQuestionScoresHigherAgainstThePassageThatAnswersIt() {
        float[] question = HashingEmbeddingModel.vectorFor("How do I avoid a dual write to Kafka?");
        float[] right = HashingEmbeddingModel.vectorFor(
                "The transactional outbox writes the event to a table in the same transaction, "
                        + "avoiding the dual write to Kafka and the database.");
        float[] wrong = HashingEmbeddingModel.vectorFor(
                "Hexagonal architecture puts ports at the boundary and adapters outside them.");

        double onTopic = HashingEmbeddingModel.cosine(question, right);
        double offTopic = HashingEmbeddingModel.cosine(question, wrong);

        assertThat(onTopic).isGreaterThan(offTopic);
        assertThat(offTopic).isLessThan(0.10);
    }

    /** Every vector has the same dimension, including for input the tokeniser throws away. */
    @Test
    void everyVectorHasTheStoredDimension() {
        assertThat(HashingEmbeddingModel.vectorFor("outbox")).hasSize(HashingEmbeddingModel.DIMENSIONS);
        assertThat(HashingEmbeddingModel.vectorFor("")).hasSize(HashingEmbeddingModel.DIMENSIONS);
        assertThat(HashingEmbeddingModel.vectorFor("a of the")).hasSize(HashingEmbeddingModel.DIMENSIONS);
    }

    @Test
    void embeddingTheSameTextTwiceGivesTheSameVector() {
        assertThat(HashingEmbeddingModel.vectorFor("chunking dominates model choice"))
                .isEqualTo(HashingEmbeddingModel.vectorFor("chunking dominates model choice"));
    }
}
