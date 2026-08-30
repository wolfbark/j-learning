package dev.vlearning.airag;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param corpusPaths       Spring resource patterns; the app reads the real repository at runtime
 *                          ({@code file:../docs/research/*.md}), tests read a frozen copy from
 *                          {@code classpath*:corpus/*.md}.
 * @param topK              how many passages retrieval returns.
 * @param similarityThreshold minimum cosine similarity (0..1) a passage must reach to be used as
 *                          context. 0.0 means "accept anything the index returns" — which is the
 *                          starting point, and the reason step 4 exists.
 * @param maxChunkChars     upper bound on a chunk's size.
 * @param overlapTokens     how many tokens of the previous chunk to repeat at the start of the next.
 */
@ConfigurationProperties("rag")
public record RagProperties(
        List<String> corpusPaths,
        int topK,
        double similarityThreshold,
        int maxChunkChars,
        int overlapTokens) {
}
