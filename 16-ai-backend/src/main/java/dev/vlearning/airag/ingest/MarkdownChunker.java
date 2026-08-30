package dev.vlearning.airag.ingest;

import java.util.ArrayList;
import java.util.List;

import dev.vlearning.airag.RagProperties;
import org.springframework.stereotype.Component;

/**
 * Splits markdown into retrievable chunks.
 *
 * <p>This is step 2's refactoring subject. It works, it is fast, and it destroys retrieval quality.
 */
@Component
public class MarkdownChunker {

    private static final int CHUNK_SIZE = 200;

    private final RagProperties properties;

    public MarkdownChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<Chunk> chunk(SourceDocument source) {
        var flattened = source.markdown().replaceAll("\\s+", " ").trim();
        var chunks = new ArrayList<Chunk>();
        for (int offset = 0, index = 0; offset < flattened.length(); offset += CHUNK_SIZE, index++) {
            int end = Math.min(offset + CHUNK_SIZE, flattened.length());
            chunks.add(new Chunk(source.name(), "", index, flattened.substring(offset, end)));
        }
        return chunks;
    }
}
