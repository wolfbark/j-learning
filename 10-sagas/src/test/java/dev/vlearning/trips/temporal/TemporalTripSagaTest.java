package dev.vlearning.trips.temporal;

import java.util.UUID;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6 — the same saga on a real Temporal server. NOT part of the pristine
 * build: it runs only when TEMPORAL_ADDRESS is set, e.g.
 *
 * <pre>
 * docker compose -f docker-compose.temporal.yml up -d
 * TEMPORAL_ADDRESS=127.0.0.1:7233 mvn test -Dtest=TemporalTripSagaTest
 * </pre>
 *
 * Afterwards, open http://localhost:8233 and read both workflows' Event
 * History — every activity, retry and compensation your step-4 code tracked
 * by hand, recorded by the engine.
 */
@EnabledIfEnvironmentVariable(named = "TEMPORAL_ADDRESS", matches = ".+")
class TemporalTripSagaTest {

    private static final String TASK_QUEUE = "trip-booking-test-" + UUID.randomUUID();

    private static WorkflowServiceStubs service;
    private static WorkflowClient client;
    private static WorkerFactory factory;
    private static SimulatedTripActivities activities;

    @BeforeAll
    static void startWorker() {
        service = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget(System.getenv("TEMPORAL_ADDRESS"))
                .build());
        client = WorkflowClient.newInstance(service);
        factory = WorkerFactory.newInstance(client);
        activities = new SimulatedTripActivities();
        var worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TripBookingWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
    }

    @AfterAll
    static void stopWorker() {
        factory.shutdown();
        service.shutdown();
    }

    @Test
    void happyPathConfirms() {
        var result = bookTrip("happy-" + UUID.randomUUID(), false);

        assertThat(result).startsWith("CONFIRMED");
        assertThat(activities.journal())
                .containsSubsequence("reserveFlight", "reserveHotel", "capturePayment");
    }

    @Test
    void fullHotelTriggersCompensationInReverseOrder() {
        var result = bookTrip("doomed-" + UUID.randomUUID(), true);

        assertThat(result).startsWith("REJECTED");
        assertThat(activities.journal())
                .containsSubsequence("reserveFlight", "reserveHotel FAILED", "cancelFlight");
        assertThat(activities.journal()).doesNotContain("capturePayment", "cancelHotel");
    }

    private String bookTrip(String tripId, boolean hotelIsFull) {
        var workflow = client.newWorkflowStub(TripBookingWorkflow.class, WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId(tripId)
                .build());
        return workflow.bookTrip(tripId, hotelIsFull);
    }
}
