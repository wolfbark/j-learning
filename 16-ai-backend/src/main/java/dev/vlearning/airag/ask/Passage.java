package dev.vlearning.airag.ask;

import dev.vlearning.airag.ingest.Chunk;
import org.springframework.ai.document.Document;

/** A retrieval hit, flattened out of {@link Document} so the rest of the code reads cleanly. */
public record Passage(String sourceFile, String heading, int chunkIndex, String text, double score) {

    public static Passage of(Document document) {
        var metadata = document.getMetadata();
        return new Passage(
                String.valueOf(metadata.getOrDefault(Chunk.SOURCE, "?")),
                String.valueOf(metadata.getOrDefault(Chunk.HEADING, "")),
                Integer.parseInt(String.valueOf(metadata.getOrDefault(Chunk.CHUNK_INDEX, "-1"))),
                document.getText(),
                document.getScore() == null ? 0.0 : document.getScore());
    }

    public Citation toCitation() {
        return new Citation(sourceFile, heading, chunkIndex);
    }
}
