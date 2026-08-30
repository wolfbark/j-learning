package dev.vlearning.trips.temporal;

import java.util.UUID;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;

/**
 * Step 6's demo. Start the server first:
 *
 * <pre>
 * docker compose -f docker-compose.temporal.yml up -d
 * mvn -q compile exec:java -Dexec.mainClass=dev.vlearning.trips.temporal.TemporalSagaRunner
 * </pre>
 *
 * Runs the trip saga twice — once happy, once with a full hotel — then leaves
 * the worker alive so you can explore http://localhost:8233 : open a workflow
 * and read its Event History. That history IS your saga_instance table, kept
 * per step instead of per saga, by the engine instead of by you.
 *
 * <p>Crash experiment: run again, and Ctrl+C the runner while a workflow is in
 * flight (add a Workflow.sleep between steps to widen the window). The workflow
 * shows as Running in the UI with no worker alive; restart the runner and it
 * completes from where it stopped. That is checkpoint 5, done by the engine.
 */
public final class TemporalSagaRunner {

    public static final String TASK_QUEUE = "trip-booking";

    private TemporalSagaRunner() {}

    public static void main(String[] args) {
        String address = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(address).build());
        WorkflowClient client = WorkflowClient.newInstance(service);

        WorkerFactory factory = WorkerFactory.newInstance(client);
        var worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TripBookingWorkflowImpl.class);
        worker.registerActivitiesImplementations(new SimulatedTripActivities());
        factory.start();

        System.out.println(run(client, false));
        System.out.println(run(client, true));
        System.out.println("Worker still running — inspect the workflows at http://localhost:8233 (Ctrl+C to stop).");
    }

    private static String run(WorkflowClient client, boolean hotelIsFull) {
        String tripId = "trip-" + UUID.randomUUID().toString().substring(0, 8);
        TripBookingWorkflow workflow = client.newWorkflowStub(TripBookingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId(tripId)
                        .build());
        return tripId + " → " + workflow.bookTrip(tripId, hotelIsFull);
    }
}
