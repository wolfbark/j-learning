package dev.vlearning.airag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.vlearning.airag.ask.Passage;
import dev.vlearning.airag.ask.RagService;
import dev.vlearning.airag.ingest.Chunk;
import dev.vlearning.airag.ingest.CorpusLoader;
import dev.vlearning.airag.ingest.MarkdownChunker;
import dev.vlearning.airag.ingest.SourceDocument;
import dev.vlearning.airag.model.HashingEmbeddingModel;
import dev.vlearning.airag.support.GoldenSet;
import dev.vlearning.airag.support.PgVectorTestBase;
import dev.vlearning.airag.support.TestModels;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Checkpoint 2 — retrieval quality. Four properties, each of which the 200-character chunker
 * violates, and each of which is a real production requirement.
 */
@SpringBootTest(properties = "rag.top-k=3")
@Import(TestModels.class)
class Checkpoint2RetrievalQualityTest extends PgVectorTestBase {

    @Autowired
    CorpusLoader loader;

    @Autowired
    MarkdownChunker chunker;

    @Autowired
    RagService ragService;

    private List<Chunk> allChunks() {
        return this.loader.load().stream().flatMap(source -> this.chunker.chunk(source).stream()).toList();
    }

    /** Without a heading you cannot cite, filter, or debug. Metadata is a feature, not decoration. */
    @Test
    void everyChunkCarriesTheHeadingItLivesUnder() {
        assertThat(allChunks()).isNotEmpty().allSatisfy(chunk -> {
            assertThat(chunk.heading()).as("heading of chunk %s of %s", chunk.chunkIndex(), chunk.sourceFile())
                    .isNotBlank();
            assertThat(chunk.sourceFile()).isNotBlank();
        });
    }

    /**
     * A chunk whose boundary falls inside a word has an embedding that is partly noise. Every chunk
     * must be a <em>verbatim, word-aligned slice</em> of its source file — which also means you put
     * the heading in the metadata rather than splicing a breadcrumb into the text.
     */
    @Test
    void everyChunkIsAWordAlignedSliceOfItsSource() {
        for (SourceDocument source : this.loader.load()) {
            String text = source.markdown();
            for (Chunk chunk : this.chunker.chunk(source)) {
                int at = text.indexOf(chunk.text());
                assertThat(at)
                        .as("chunk %s of %s is not a verbatim slice of the file: '%s...'",
                                chunk.chunkIndex(), source.name(),
                                chunk.text().substring(0, Math.min(60, chunk.text().length())))
                        .isNotNegative();
                int end = at + chunk.text().length();
                assertThat(at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1)))
                        .as("chunk %s of %s starts mid-word", chunk.chunkIndex(), source.name())
                        .isTrue();
                assertThat(end == text.length() || !Character.isLetterOrDigit(text.charAt(end)))
                        .as("chunk %s of %s ends mid-word", chunk.chunkIndex(), source.name())
                        .isTrue();
            }
        }
    }

    /**
     * Overlap is why a sentence that straddles a boundary is still retrievable: two chunks split
     * out of the <em>same section</em> must share a run of tokens.
     */
    @Test
    void consecutiveChunksOverlap() {
        for (SourceDocument source : this.loader.load()) {
            List<Chunk> chunks = this.chunker.chunk(source);
            for (int i = 1; i < chunks.size(); i++) {
                if (!chunks.get(i).heading().equals(chunks.get(i - 1).heading())) {
                    continue;
                }
                List<String> previous = HashingEmbeddingModel.tokenize(chunks.get(i - 1).text());
                List<String> current = HashingEmbeddingModel.tokenize(chunks.get(i).text());
                if (previous.size() < 6 || current.size() < 6) {
                    continue;
                }
                assertThat(sharesARunOfAtLeast(3, previous, current))
                        .as("chunks %s and %s of %s share no run of tokens", i - 1, i, source.name())
                        .isTrue();
            }
        }
    }

    /** The one that matters: does the right passage come back for a real question? */
    @Test
    void goldenQuestionsRetrieveTheExpectedHeading() {
        this.ingestionService.ingestAll();

        var misses = new java.util.ArrayList<String>();
        for (var expected : GoldenSet.CASES) {
            List<Passage> passages = this.ragService.retrieve(expected.question());
            boolean hit = passages.stream()
                    .anyMatch(passage -> passage.sourceFile().equals(expected.expectedSource())
                            && passage.heading().contains(expected.expectedHeading()));
            if (!hit) {
                misses.add("%s -> expected %s / '%s', got %s".formatted(expected.question(),
                        expected.expectedSource(), expected.expectedHeading(),
                        passages.stream().map(p -> p.sourceFile() + " / '" + p.heading() + "'").toList()));
            }
        }
        assertThat(misses).as("top-3 retrieval misses").isEmpty();
    }

    private static boolean sharesARunOfAtLeast(int length, List<String> previous, List<String> current) {
        for (int start = Math.max(0, previous.size() - 200); start + length <= previous.size(); start++) {
            List<String> run = previous.subList(start, start + length);
            for (int at = 0; at + length <= Math.min(current.size(), 200); at++) {
                if (current.subList(at, at + length).equals(run)) {
                    return true;
                }
            }
        }
        return false;
    }
}
