package dev.vlearning.trips.booking;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The booking service's own table. Nobody else touches it — the other services
 * do not even know it exists (ArchUnit agrees).
 */
@Repository
public class TripRepository {

    private final JdbcClient jdbc;

    public TripRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPending(UUID tripId, String traveller, String destination, BigDecimal price) {
        jdbc.sql("""
                INSERT INTO trips (trip_id, traveller, destination, price, status)
                VALUES (:tripId, :traveller, :destination, :price, 'PENDING')
                ON CONFLICT (trip_id) DO NOTHING""")
                .param("tripId", tripId).param("traveller", traveller)
                .param("destination", destination).param("price", price)
                .update();
    }

    public void updateStatus(UUID tripId, TripStatus status) {
        jdbc.sql("UPDATE trips SET status = :status, updated_at = now() WHERE trip_id = :tripId")
                .param("tripId", tripId).param("status", status.name())
                .update();
    }

    public Optional<Trip> find(UUID tripId) {
        return jdbc.sql("SELECT trip_id, traveller, destination, price, status FROM trips WHERE trip_id = :tripId")
                .param("tripId", tripId)
                .query((rs, rowNum) -> new Trip(
                        rs.getObject("trip_id", UUID.class),
                        rs.getString("traveller"),
                        rs.getString("destination"),
                        rs.getBigDecimal("price"),
                        TripStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    public record Trip(UUID tripId, String traveller, String destination, BigDecimal price, TripStatus status) {}
}
