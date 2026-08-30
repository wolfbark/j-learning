package dev.vlearning.trips.orchestration;

/**
 * Where the saga currently waits. The forward steps mirror the trip's shape
 * (flight → hotel → payment); the compensating steps run the SAME shape in
 * reverse. Checkpoint 4's tests assert these exact names in the
 * {@code saga_instance} table — "where is booking #42 stuck?" must be
 * answerable by one SELECT.
 */
public enum SagaStep {
    AWAITING_FLIGHT,
    AWAITING_HOTEL,
    AWAITING_PAYMENT,
    COMPENSATING_HOTEL,
    COMPENSATING_FLIGHT,
    DONE
}
