package dev.vlearning.airag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vlearning.airag.ask.Passage;
import dev.vlearning.airag.ask.RagService;
import dev.vlearning.airag.support.PgVectorTestBase;
import dev.vlearning.airag.support.TestModels;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ENABLED end-to-end proof that the plumbing works: five markdown files go into a real pgvector
 * container, come back out as passages, and reach an answer over HTTP — with no API key anywhere.
 *
 * <p>It asserts only that the pipeline runs and points at the right *file*. Retrieval *quality*
 * (the right heading, reliably) is step 2's checkpoint, and it fails today.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestModels.class)
class RagPipelineSmokeTest extends PgVectorTestBase {

    @Autowired
    RagService ragService;

    @Autowired
    MockMvc mvc;

    @Test
    void ingestsTheFrozenCorpusIntoPgvector() {
        var report = this.ingestionService.ingestAll();

        assertThat(report.files()).isEqualTo(5);
        assertThat(report.chunks()).isGreaterThan(20);
        assertThat(storedVectorCount()).isPositive();
    }

    @Test
    void retrievesPassagesFromTheFileThatDiscussesTheTopic() {
        this.ingestionService.ingestAll();

        var passages = this.ragService.retrieve(
                "How do I avoid a dual write when publishing events to Kafka?");

        assertThat(passages).isNotEmpty();
        assertThat(passages).allSatisfy(passage -> assertThat(passage.sourceFile()).endsWith(".md"));
        assertThat(passages).extracting(Passage::sourceFile).contains("event-driven.md");
    }

    @Test
    void answersOverHttpWithCitations() throws Exception {
        this.ingestionService.ingestAll();

        this.mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON).content("""
                {"question": "What is the transactional outbox pattern?"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations[0].sourceFile").isNotEmpty());
    }
}
