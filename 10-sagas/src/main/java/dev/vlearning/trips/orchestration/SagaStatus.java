package dev.vlearning.trips.orchestration;

/**
 * The saga's overall verdict. {@code COMPLETED} and {@code COMPENSATED} are
 * both SUCCESSFUL ends of a saga — the second one just means "successfully
 * undone". A saga that can neither finish nor unwind is stuck in
 * {@code RUNNING}/{@code COMPENSATING}, and that is exactly what the
 * one-SELECT diagnosis test looks for.
 */
public enum SagaStatus {
    RUNNING,
    COMPENSATING,
    COMPLETED,
    COMPENSATED
}
