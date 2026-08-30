package dev.vlearning.airag.eval;

/**
 * One graded question. {@code expectedSource} plus {@code expectedHeading} is the citation the
 * answer must contain to count as correct — a "golden set" entry, hand-written by someone who knows
 * the corpus. There is no shortcut around that work.
 */
public record EvalCase(String question, String expectedSource, String expectedHeading) {
}
