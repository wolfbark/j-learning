package dev.vlearning.airag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vlearning.airag.ingest.Chunk;
import dev.vlearning.airag.support.CountingEmbeddingModel;
import dev.vlearning.airag.support.PgVectorTestBase;
import dev.vlearning.airag.support.TestModels;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Checkpoint 3 — ingestion that a production system could survive: batched embedding calls, stable
 * identity, and metadata that actually reaches the database.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
@SpringBootTest
@Import(TestModels.class)
class Checkpoint3IngestionTest extends PgVectorTestBase {

    @Autowired
    CountingEmbeddingModel embeddingModel;

    /**
     * One embedding request per chunk is the default, and against a hosted model it is one HTTP
     * round trip and one invoice line per chunk. Batch it.
     */
    @Test
    void embeddingRequestsAreBatched() {
        this.embeddingModel.resetRequestCount();

        var report = this.ingestionService.ingestAll();

        assertThat(report.chunks()).isGreaterThan(20);
        assertThat(this.embeddingModel.requestCount())
                .as("embedding round trips for %s chunks", report.chunks())
                .isLessThanOrEqualTo(8);
    }

    /** Re-ingestion is a nightly job, a redeploy, a retry. It must converge, not accumulate. */
    @Test
    void reIngestingTheSameCorpusDoesNotDuplicateAnything() {
        var first = this.ingestionService.ingestAll();
        long afterFirst = storedVectorCount();

        var second = this.ingestionService.ingestAll();
        long afterSecond = storedVectorCount();

        assertThat(afterFirst).isEqualTo(first.chunks());
        assertThat(afterSecond).isEqualTo(afterFirst);
        assertThat(second.chunks()).isEqualTo(first.chunks());
    }

    @Test
    void everyStoredRowCarriesSourceHeadingAndChunkIndex() {
        this.ingestionService.ingestAll();

        long complete = this.jdbc.sql("""
                SELECT count(*) FROM vector_store
                 WHERE metadata->>'%s' IS NOT NULL
                   AND metadata->>'%s' <> ''
                   AND metadata->>'%s' IS NOT NULL
                """.formatted(Chunk.SOURCE, Chunk.HEADING, Chunk.CHUNK_INDEX))
                .query(Long.class).single();

        assertThat(complete).isEqualTo(storedVectorCount());
    }
}
