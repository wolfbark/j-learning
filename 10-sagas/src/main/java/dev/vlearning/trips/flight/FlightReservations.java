package dev.vlearning.trips.flight;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The flight service's own table. Inserts are idempotent — redelivery-proof. */
@Repository
public class FlightReservations {

    private final JdbcClient jdbc;

    public FlightReservations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertReserved(UUID tripId, String flightNumber) {
        jdbc.sql("""
                INSERT INTO flight_reservations (trip_id, flight_number, status)
                VALUES (:tripId, :flightNumber, 'RESERVED')
                ON CONFLICT (trip_id) DO NOTHING""")
                .param("tripId", tripId).param("flightNumber", flightNumber)
                .update();
    }

    /** @return number of rows flipped — 0 means there was nothing to cancel. */
    public int markCancelled(UUID tripId) {
        return jdbc.sql("""
                UPDATE flight_reservations SET status = 'CANCELLED', updated_at = now()
                WHERE trip_id = :tripId AND status = 'RESERVED'""")
                .param("tripId", tripId)
                .update();
    }

    public Optional<String> statusOf(UUID tripId) {
        return jdbc.sql("SELECT status FROM flight_reservations WHERE trip_id = :tripId")
                .param("tripId", tripId)
                .query(String.class)
                .optional();
    }
}
