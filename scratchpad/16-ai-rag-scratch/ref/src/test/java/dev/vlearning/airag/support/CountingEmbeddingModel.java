package dev.vlearning.airag.support;

import java.util.concurrent.atomic.AtomicInteger;

import dev.vlearning.airag.model.HashingEmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * The hashing embedder plus a counter on {@code call(EmbeddingRequest)} — the method that would be
 * one HTTP round trip and one line on the invoice against a hosted model. Step 3's checkpoint turns
 * "batch your embeddings" from advice into a number.
 */
public class CountingEmbeddingModel implements EmbeddingModel {

    private final HashingEmbeddingModel delegate = new HashingEmbeddingModel();

    private final AtomicInteger requests = new AtomicInteger();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        this.requests.incrementAndGet();
        return this.delegate.call(request);
    }

    @Override
    public float[] embed(Document document) {
        return this.delegate.embed(document);
    }

    @Override
    public int dimensions() {
        return this.delegate.dimensions();
    }

    public int requestCount() {
        return this.requests.get();
    }

    public void resetRequestCount() {
        this.requests.set(0);
    }
}
