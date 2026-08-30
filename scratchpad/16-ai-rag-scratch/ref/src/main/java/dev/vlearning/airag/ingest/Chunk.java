package dev.vlearning.airag.ingest;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;

/**
 * One retrievable unit of the corpus. The metadata keys travel with the vector into pgvector and
 * come back on every search hit — they are what makes a citation possible at all.
 */
public record Chunk(String sourceFile, String heading, int chunkIndex, String text) {

    public static final String SOURCE = "source";
    public static final String HEADING = "heading";
    public static final String CHUNK_INDEX = "chunk_index";

    public Document toDocument() {
        return Document.builder()
                .id(documentId())
                .text(text)
                .metadata(Map.of(SOURCE, sourceFile, HEADING, heading, CHUNK_INDEX, chunkIndex))
                .build();
    }

    public String documentId() {
        return UUID.nameUUIDFromBytes((sourceFile + "#" + chunkIndex).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
