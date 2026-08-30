package dev.vlearning.airag.ingest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * The write half of the pipeline: read files, chunk, embed, store.
 *
 * <p>The embedding step is invisible here on purpose — {@code VectorStore.add} calls the
 * {@code EmbeddingModel} for you. That is a real convenience and a real trap: one HTTP round trip
 * per {@code add} call against a hosted model, billed per token.
 */
@Service
public class IngestionService {

    private final CorpusLoader loader;
    private final MarkdownChunker chunker;
    private final VectorStore vectorStore;

    public IngestionService(CorpusLoader loader, MarkdownChunker chunker, VectorStore vectorStore) {
        this.loader = loader;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
    }

    public IngestionReport ingestAll() {
        var sources = loader.load();
        var documents = new ArrayList<Document>();
        for (SourceDocument source : sources) {
            for (Chunk chunk : chunker.chunk(source)) {
                documents.add(chunk.toDocument());
            }
        }
        store(documents);
        return new IngestionReport(sources.size(), documents.size());
    }

    private static final int BATCH = 128;

    private void store(List<Document> documents) {
        for (int from = 0; from < documents.size(); from += BATCH) {
            vectorStore.add(documents.subList(from, Math.min(from + BATCH, documents.size())));
        }
    }
}
