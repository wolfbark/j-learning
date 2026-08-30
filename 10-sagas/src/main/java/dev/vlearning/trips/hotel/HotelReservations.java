package dev.vlearning.trips.hotel;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The hotel service's own table. Inserts are idempotent — redelivery-proof. */
@Repository
public class HotelReservations {

    private final JdbcClient jdbc;

    public HotelReservations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertReserved(UUID tripId, String hotelName) {
        jdbc.sql("""
                INSERT INTO hotel_reservations (trip_id, hotel_name, status)
                VALUES (:tripId, :hotelName, 'RESERVED')
                ON CONFLICT (trip_id) DO NOTHING""")
                .param("tripId", tripId).param("hotelName", hotelName)
                .update();
    }

    /** @return number of rows flipped — 0 means there was nothing to cancel. */
    public int markCancelled(UUID tripId) {
        return jdbc.sql("""
                UPDATE hotel_reservations SET status = 'CANCELLED', updated_at = now()
                WHERE trip_id = :tripId AND status = 'RESERVED'""")
                .param("tripId", tripId)
                .update();
    }

    public Optional<String> statusOf(UUID tripId) {
        return jdbc.sql("SELECT status FROM hotel_reservations WHERE trip_id = :tripId")
                .param("tripId", tripId)
                .query(String.class)
                .optional();
    }
}
