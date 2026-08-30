package dev.vlearning.airag.ask;

import java.util.List;

/**
 * The shape step 6 asks the model to produce directly (Spring AI structured output).
 *
 * @param confidence {@code high} / {@code low} / {@code none} — {@code none} means the service
 *                   refused, and a refusal must never carry citations.
 */
public record Answer(String answer, List<Citation> citations, String confidence) {

    public static final String REFUSAL =
            "I don't know — the training material I have indexed does not cover that.";

    public static Answer refused() {
        return new Answer(REFUSAL, List.of(), "none");
    }

    public boolean isRefusal() {
        return "none".equals(confidence);
    }
}
