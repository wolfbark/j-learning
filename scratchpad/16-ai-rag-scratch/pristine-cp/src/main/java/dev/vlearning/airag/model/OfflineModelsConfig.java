package dev.vlearning.airag.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The offline model pair, active while {@code rag.models=offline} (the default). Set
 * {@code rag.models=real}, add a {@code spring-ai-starter-model-*} dependency and an API key, and
 * these beans step aside without a single change anywhere else in the application.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rag.models", havingValue = "offline", matchIfMissing = true)
public class OfflineModelsConfig {

    @Bean
    EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }

    @Bean
    ChatModel chatModel() {
        return new ExtractiveChatModel();
    }
}
