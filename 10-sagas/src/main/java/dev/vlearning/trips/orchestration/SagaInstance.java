package dev.vlearning.trips.orchestration;

import java.util.UUID;

/**
 * One row in {@code saga_instance} — the orchestrator's entire memory of one
 * trip. This is the state a durable-execution engine would persist for you;
 * in step 4 you persist it yourself, and in step 5 you prove that surviving a
 * crash takes nothing more than this row plus redelivery.
 */
public record SagaInstance(UUID tripId, SagaStep currentStep, SagaStatus status) {}
