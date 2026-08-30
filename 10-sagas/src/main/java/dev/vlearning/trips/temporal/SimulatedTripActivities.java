package dev.vlearning.trips.temporal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory stand-ins for the three services. The journal records every call
 * in order, so a test (or your own eyes) can verify the compensation sequence.
 * {@code hotelIsFull} throws a NON-retryable failure — chaos, Temporal flavor;
 * a retryable one would simply be retried per the activity's RetryOptions.
 */
public class SimulatedTripActivities implements TripActivities {

    private static final Logger log = LoggerFactory.getLogger(SimulatedTripActivities.class);

    private final List<String> journal = new CopyOnWriteArrayList<>();

    @Override
    public String reserveFlight(String tripId) {
        record("reserveFlight", tripId);
        return "flight VL-0042";
    }

    @Override
    public void cancelFlight(String tripId) {
        record("cancelFlight", tripId);
    }

    @Override
    public String reserveHotel(String tripId, boolean hotelIsFull) {
        if (hotelIsFull) {
            record("reserveHotel FAILED", tripId);
            throw ApplicationFailure.newNonRetryableFailure("No rooms left", "HotelFull");
        }
        record("reserveHotel", tripId);
        return "Grand Hotel Kyoto";
    }

    @Override
    public void cancelHotel(String tripId) {
        record("cancelHotel", tripId);
    }

    @Override
    public String capturePayment(String tripId) {
        record("capturePayment", tripId);
        return "payment of 1499.00";
    }

    @Override
    public void refundPayment(String tripId) {
        record("refundPayment", tripId);
    }

    public List<String> journal() {
        return List.copyOf(journal);
    }

    private void record(String action, String tripId) {
        journal.add(action);
        log.info("activity [{}]: {}", tripId, action);
    }
}
