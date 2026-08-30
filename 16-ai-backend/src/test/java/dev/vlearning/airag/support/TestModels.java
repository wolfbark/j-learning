package dev.vlearning.airag.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application's offline model pair with instrumented test doubles. Imported by every
 * container test; nothing on this classpath can reach a model provider, so there is no key to leak
 * and no request to bill.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestModels {

    @Bean
    @Primary
    ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }

    @Bean
    @Primary
    CountingEmbeddingModel countingEmbeddingModel() {
        return new CountingEmbeddingModel();
    }
}
