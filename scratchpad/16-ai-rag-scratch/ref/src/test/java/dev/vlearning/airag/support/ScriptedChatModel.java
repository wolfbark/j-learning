package dev.vlearning.airag.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import dev.vlearning.airag.model.ExtractiveChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A {@link ChatModel} you can put words in the mouth of. Every prompt is recorded; scripted
 * responses are consumed in order; when the script runs out it delegates to the offline
 * {@link ExtractiveChatModel}, so tests that do not care about generation still get sane answers.
 *
 * <p>This is the whole trick behind testing LLM integrations: {@code ChatModel} is a one-method
 * interface. A test double for it is cheaper than a WireMock stub and infinitely more precise.
 */
public class ScriptedChatModel implements ChatModel {

    private final ChatModel fallback = new ExtractiveChatModel();

    private final Deque<Function<Prompt, ChatResponse>> script = new ArrayDeque<>();

    private final List<Prompt> prompts = Collections.synchronizedList(new ArrayList<>());

    private final ChatOptions defaultOptions = this.fallback.getOptions();

    @Override
    public ChatResponse call(Prompt prompt) {
        this.prompts.add(prompt);
        Function<Prompt, ChatResponse> next = this.script.poll();
        return next != null ? next.apply(prompt) : this.fallback.call(prompt);
    }

    @Override
    public ChatOptions getOptions() {
        return this.defaultOptions;
    }

    /** Script the model to request a tool call, exactly as a real model would. */
    public ScriptedChatModel scriptToolCall(String toolName, String jsonArguments) {
        this.script.add(prompt -> {
            var toolCall = new AssistantMessage.ToolCall(UUID.randomUUID().toString(), "function",
                    toolName, jsonArguments);
            var message = AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
            return new ChatResponse(List.of(new Generation(message)));
        });
        return this;
    }

    public ScriptedChatModel scriptText(String text) {
        this.script.add(prompt -> respond(prompt, text));
        return this;
    }

    /**
     * Script a reply that quotes whatever the tools returned — the second half of a tool-calling
     * round trip, where the model finally sees the tool output.
     */
    public ScriptedChatModel scriptQuotingToolResults(String prefix) {
        this.script.add(prompt -> respond(prompt, prefix + toolResultsIn(prompt)));
        return this;
    }

    /**
     * Wraps a scripted answer the way the prompt asks for it: prose normally, and a JSON
     * {@code Answer} object once step 6 has the structured-output converter appending a schema.
     * A real model behaves the same way — the schema in the prompt is what changes its reply.
     */
    private static ChatResponse respond(Prompt prompt, String text) {
        String body = prompt.getContents().contains("$schema")
                ? "{\"answer\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                        + "\",\"citations\":[],\"confidence\":\"low\"}"
                : text;
        return new ChatResponse(List.of(new Generation(new AssistantMessage(body))));
    }

    public static String toolResultsIn(Prompt prompt) {
        var results = new StringBuilder();
        for (Message message : prompt.getInstructions()) {
            if (message instanceof ToolResponseMessage toolResponses) {
                toolResponses.getResponses()
                        .forEach(response -> results.append(response.responseData()).append(" "));
            }
        }
        return results.toString().trim();
    }

    public List<Prompt> prompts() {
        return List.copyOf(this.prompts);
    }

    public int promptCount() {
        return this.prompts.size();
    }

    public void reset() {
        this.script.clear();
        this.prompts.clear();
    }
}
