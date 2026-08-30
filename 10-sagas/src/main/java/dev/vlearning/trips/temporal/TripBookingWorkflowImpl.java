package dev.vlearning.trips.temporal;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

/**
 * Your step-4 orchestrator, rewritten as straight-line code. Read it next to
 * your {@code TripSagaOrchestrator}:
 *
 * <ul>
 *   <li>the {@code saga_instance} table → the position in this method,
 *       persisted by Temporal as event history;</li>
 *   <li>your reverse-order compensation states → {@link Saga}, which records a
 *       compensation after each completed step and replays them backwards on
 *       failure;</li>
 *   <li>your checkpoint-5 crash/resume machinery → free: kill the worker at any
 *       line, restart it, and execution continues from the last completed
 *       activity.</li>
 * </ul>
 *
 * The one new rule: workflow code must be deterministic (no random, no clock,
 * no I/O — that all belongs in activities), because Temporal re-executes it
 * against recorded history to restore the "call stack".
 */
public class TripBookingWorkflowImpl implements TripBookingWorkflow {

    private final TripActivities activities = Workflow.newActivityStub(TripActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    @Override
    public String bookTrip(String tripId, boolean hotelIsFull) {
        var saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());
        try {
            String flight = activities.reserveFlight(tripId);
            saga.addCompensation(activities::cancelFlight, tripId);

            String hotel = activities.reserveHotel(tripId, hotelIsFull);
            saga.addCompensation(activities::cancelHotel, tripId);

            String payment = activities.capturePayment(tripId);
            saga.addCompensation(activities::refundPayment, tripId);

            return "CONFIRMED — %s, %s, %s".formatted(flight, hotel, payment);
        } catch (ActivityFailure e) {
            saga.compensate();
            return "REJECTED — compensated: " + e.getCause().getMessage();
        }
    }
}
