package dev.vlearning.airag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.vlearning.airag.RagProperties;
import dev.vlearning.airag.ingest.Chunk;
import dev.vlearning.airag.ingest.MarkdownChunker;
import dev.vlearning.airag.ingest.SourceDocument;
import org.junit.jupiter.api.Test;

/**
 * ENABLED. The properties any chunker must have — true of the deliberately bad one you start with,
 * and still true after step 2. This is your regression net while you rewrite it.
 */
class ChunkerContractTest {

    private static final String MARKDOWN = """
            # Guide

            ## Retrieval

            Retrieval finds candidate passages by vector similarity. Top-k is a tuning knob,
            not a constant, and the right value depends on how long your chunks are.

            ## Grounding

            Grounding means the answer may only use the retrieved passages. A model with no
            relevant context must refuse rather than improvise.
            """;

    // maxChunkChars 800, overlapTokens 40 — the knobs step 2 gives you.
    private final MarkdownChunker chunker =
            new MarkdownChunker(new RagProperties(List.of(), 4, 0.0, 800, 40));

    @Test
    void producesMoreThanOneChunkAndLosesAlmostNothing() {
        List<Chunk> chunks = this.chunker.chunk(new SourceDocument("guide.md", MARKDOWN));

        assertThat(chunks).hasSizeGreaterThan(1);

        int totalChunkChars = chunks.stream().mapToInt(chunk -> chunk.text().length()).sum();
        assertThat(totalChunkChars)
                .as("chunking must not silently drop half the corpus")
                .isGreaterThan(MARKDOWN.length() / 2);
    }

    @Test
    void everyChunkIsNonBlankAndKnowsWhereItCameFrom() {
        List<Chunk> chunks = this.chunker.chunk(new SourceDocument("guide.md", MARKDOWN));

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.text()).isNotBlank();
            assertThat(chunk.sourceFile()).isEqualTo("guide.md");
        });
    }

    @Test
    void chunkIndicesAreContiguousFromZero() {
        List<Chunk> chunks = this.chunker.chunk(new SourceDocument("guide.md", MARKDOWN));

        assertThat(chunks.stream().map(Chunk::chunkIndex).toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void anEmptyDocumentProducesNoChunks() {
        assertThat(this.chunker.chunk(new SourceDocument("empty.md", "   \n\n  "))).isEmpty();
    }
}
