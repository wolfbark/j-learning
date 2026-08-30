package dev.vlearning.trips.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * The whole trip saga as ONE method signature. Compare with what step 4 needed:
 * an events topic, three command topics, a saga_instance table, and a state
 * machine dispatching on eleven message types. Here the "state machine" is the
 * Java call stack, and Temporal persists it between (and across) crashes.
 */
@WorkflowInterface
public interface TripBookingWorkflow {

    @WorkflowMethod
    String bookTrip(String tripId, boolean hotelIsFull);
}
