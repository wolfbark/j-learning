package dev.vlearning.airag.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * A deterministic, offline {@link EmbeddingModel}: the classic <em>hashing trick</em>. Every token
 * is hashed into one of {@link #DIMENSIONS} buckets, counts are log-weighted, and the vector is
 * L2-normalised so that a dot product <em>is</em> cosine similarity.
 *
 * <p>The dimension count is a real tuning parameter, not decoration: at 256 buckets a 150-token
 * chunk collides with itself so often that an unrelated question still scores ~0.2, and no
 * similarity threshold can separate signal from noise. At 1024 the floor drops to ~0.05. Real
 * embedding models spend their dimensions on meaning instead of collision avoidance; the lesson is
 * the same either way — measure the score distribution before you pick a threshold.
 *
 * <p>Be honest about what this is: a bag of words. It scores lexical overlap, not meaning —
 * "car" and "automobile" land in unrelated buckets, where a real embedding model would place them
 * next to each other. Everything <em>around</em> it in this project (chunking, the vector store,
 * top-k retrieval, thresholds, prompt assembly, evaluation) is exactly what you would ship against
 * a real model, which is why the whole lesson runs with no API key and no network.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    /** Must match {@code spring.ai.vectorstore.pgvector.dimensions} and the Flyway migration. */
    public static final int DIMENSIONS = 1024;

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    private static final Set<String> STOPWORDS = Set.of("the", "and", "for", "that", "with", "this",
            "from", "are", "was", "were", "you", "your", "its", "has", "have", "had", "can", "could",
            "not", "but", "how", "what", "why", "when", "which", "who", "does", "did", "into", "over",
            "than", "then", "there", "their", "them", "they", "our", "one", "two", "all", "also",
            "more", "most", "some", "such", "only", "other", "new", "get", "got", "any", "out", "own",
            "per", "via", "use", "using", "used", "will", "would", "should", "must", "about");

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var results = new ArrayList<Embedding>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            results.add(new Embedding(vectorFor(instructions.get(i)), i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public static float[] vectorFor(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String token : tokenize(text)) {
            vector[Math.floorMod(stableHash(token), DIMENSIONS)] += 1.0f;
        }
        double squares = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            if (vector[i] > 0) {
                vector[i] = (float) (1 + Math.log(vector[i]));
            }
            squares += (double) vector[i] * vector[i];
        }
        double norm = Math.sqrt(squares);
        if (norm == 0) {
            // pgvector's cosine distance is undefined for the zero vector; park empty text far away
            vector[0] = 1.0f;
            return vector;
        }
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] /= (float) norm;
        }
        return vector;
    }

    public static List<String> tokenize(String text) {
        return Arrays.stream(NON_WORD.split(text.toLowerCase(Locale.ROOT)))
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOPWORDS.contains(token))
                .toList();
    }

    /** Spelled out rather than {@code String.hashCode()} so the arithmetic is inspectable. */
    private static int stableHash(String token) {
        int hash = 0;
        for (int i = 0; i < token.length(); i++) {
            hash = 31 * hash + token.charAt(i);
        }
        return hash;
    }

    /** For vectors from this model the dot product already is the cosine. */
    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
