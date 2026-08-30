package dev.vlearning.trips.temporal;

import io.temporal.activity.ActivityInterface;

/**
 * The participants, as Temporal sees them: plain method calls. Each activity
 * invocation is persisted, retried per policy, and survives worker restarts.
 * These map 1:1 to the command handlers of the Kafka version — reserve/cancel
 * per service — which is the point: participants don't change between
 * coordination styles, only the coordinator does.
 */
@ActivityInterface
public interface TripActivities {

    String reserveFlight(String tripId);

    void cancelFlight(String tripId);

    String reserveHotel(String tripId, boolean hotelIsFull);

    void cancelHotel(String tripId);

    String capturePayment(String tripId);

    void refundPayment(String tripId);
}
