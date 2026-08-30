package dev.vlearning.airag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class AiRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiRagApplication.class, args);
    }

    /**
     * The only place in this application that knows a model exists. Swap the {@link ChatModel}
     * bean (see {@code rag.models} in application.properties) and nothing below this line changes
     * — that is the whole point of the abstraction.
     */
    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
