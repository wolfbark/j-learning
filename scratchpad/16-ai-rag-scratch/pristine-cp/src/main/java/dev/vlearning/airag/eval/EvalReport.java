package dev.vlearning.airag.eval;

import java.util.List;

public record EvalReport(int total, int passed, List<String> failures) {

    public double score() {
        return total == 0 ? 0.0 : (double) passed / total;
    }

    public String summary() {
        return "%d/%d (%.0f%%)".formatted(passed, total, score() * 100);
    }
}
