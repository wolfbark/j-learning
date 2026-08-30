package dev.vlearning.airag.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * An offline {@link ChatModel} that answers <em>extractively</em>: it parses the numbered passages
 * out of the prompt this application built and quotes the best one back, with citations. No
 * network, no key, no bill, and byte-identical output for identical input.
 *
 * <p>It is not a language model and does not pretend to be one. It is here because the interesting,
 * testable, quality-determining half of RAG is retrieval, and retrieval does not need a generator
 * to be measured. Point {@code rag.models=real} at a hosted model when you want prose.
 *
 * <p>When it sees a JSON Schema in the prompt (Spring AI's structured-output converter appends one)
 * it answers in JSON instead of prose — the same two modes a real model has.
 */
public class ExtractiveChatModel implements ChatModel {

    private static final Pattern PASSAGE_HEADER =
            Pattern.compile("\\[(\\d+)] source=(\\S+) heading=\"([^\"]*)\"");

    private final ChatOptions defaultOptions = ToolCallingChatOptions.builder().build();

    private record Reference(int number, String source, String heading, String text) {
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String conversation = prompt.getContents();
        List<Reference> references = parsePassages(conversation);
        String body = wantsJson(conversation) ? asJson(references) : asProse(references);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(body))));
    }

    /**
     * Spring AI 2.0 reads a model's options through {@code getOptions()} — {@code
     * getDefaultOptions()} is deprecated and overriding it has no effect. The returned type must
     * implement {@link ToolCallingChatOptions}, or the {@code ToolCallingAdvisor} decides this model
     * cannot call tools and passes the request straight through (your tool never runs).
     */
    @Override
    public ChatOptions getOptions() {
        return this.defaultOptions;
    }

    private static List<Reference> parsePassages(String conversation) {
        var references = new ArrayList<Reference>();
        Matcher matcher = PASSAGE_HEADER.matcher(conversation);
        var starts = new ArrayList<int[]>();
        var headers = new ArrayList<Reference>();
        while (matcher.find()) {
            starts.add(new int[] { matcher.end(), matcher.start() });
            headers.add(new Reference(Integer.parseInt(matcher.group(1)), matcher.group(2),
                    matcher.group(3), ""));
        }
        for (int i = 0; i < headers.size(); i++) {
            int from = starts.get(i)[0];
            int to = (i + 1 < starts.size()) ? starts.get(i + 1)[1] : conversation.length();
            Reference header = headers.get(i);
            references.add(new Reference(header.number(), header.source(), header.heading(),
                    conversation.substring(from, Math.min(to, conversation.length())).trim()));
        }
        return references;
    }

    private static boolean wantsJson(String conversation) {
        return conversation.contains("$schema");
    }

    private static String asProse(List<Reference> references) {
        if (references.isEmpty()) {
            return "I do not know: no context passages were provided.";
        }
        Reference best = references.getFirst();
        var citations = new StringBuilder();
        for (Reference reference : references) {
            citations.append("[").append(reference.number()).append("]");
        }
        return excerpt(best.text()) + " " + citations;
    }

    private static String asJson(List<Reference> references) {
        var json = new StringBuilder("{\"answer\":\"");
        json.append(escape(references.isEmpty() ? "I do not know." : excerpt(references.getFirst().text())));
        json.append("\",\"citations\":[");
        for (int i = 0; i < references.size(); i++) {
            Reference reference = references.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"sourceFile\":\"").append(escape(reference.source()))
                    .append("\",\"heading\":\"").append(escape(reference.heading()))
                    .append("\",\"chunkIndex\":").append(i).append("}");
        }
        json.append("],\"confidence\":\"");
        json.append(references.isEmpty() ? "none" : (references.size() >= 2 ? "high" : "low"));
        return json.append("\"}").toString();
    }

    private static String excerpt(String text) {
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 480 ? collapsed : collapsed.substring(0, 480) + "...";
    }

    private static String escape(String text) {
        var out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
