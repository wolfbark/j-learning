package dev.vlearning.airag.ask;

import java.util.List;

import dev.vlearning.airag.RagProperties;
import dev.vlearning.airag.tools.LessonIndex;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    static final String SYSTEM_PROMPT = """
            You answer questions about a Java training repository.
            Use ONLY the numbered context passages provided by the user message.
            Cite the passages you used by their numbers, like [1] or [2].
            If the context does not contain the answer, say that you do not know.
            Never invent a source.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagProperties properties;
    private final LessonIndex lessonIndex;

    public RagService(VectorStore vectorStore, ChatClient chatClient, RagProperties properties,
            LessonIndex lessonIndex) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.properties = properties;
        this.lessonIndex = lessonIndex;
    }

    public Answer ask(String question) {
        List<Passage> passages = retrieve(question);
        if (passages.isEmpty()) {
            return Answer.refused();
        }
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage(question, passages))
                .tools(lessonIndex)
                .call()
                .entity(Answer.class);
    }

    public List<Passage> retrieve(String question) {
        var request = SearchRequest.builder()
                .query(question)
                .topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold())
                .build();
        return vectorStore.similaritySearch(request).stream().map(Passage::of).toList();
    }

    static String userMessage(String question, List<Passage> passages) {
        var prompt = new StringBuilder("Context passages:\n\n");
        for (int i = 0; i < passages.size(); i++) {
            Passage passage = passages.get(i);
            prompt.append("[").append(i + 1).append("] source=").append(passage.sourceFile())
                    .append(" heading=\"").append(passage.heading()).append("\"\n")
                    .append(passage.text()).append("\n\n");
        }
        return prompt.append("Question: ").append(question).toString();
    }

    static String confidenceOf(List<Passage> passages) {
        if (passages.isEmpty()) {
            return "none";
        }
        return passages.size() >= 2 ? "high" : "low";
    }

    LessonIndex lessonIndex() {
        return lessonIndex;
    }
}
