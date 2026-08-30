package dev.vlearning.trips.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The payment service's own table. Inserts are idempotent — redelivery-proof. */
@Repository
public class Payments {

    private final JdbcClient jdbc;

    public Payments(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertCaptured(UUID tripId, BigDecimal amount) {
        jdbc.sql("""
                INSERT INTO payments (trip_id, amount, status)
                VALUES (:tripId, :amount, 'CAPTURED')
                ON CONFLICT (trip_id) DO NOTHING""")
                .param("tripId", tripId).param("amount", amount)
                .update();
    }

    /** @return number of rows flipped — 0 means there was nothing to refund. */
    public int markRefunded(UUID tripId) {
        return jdbc.sql("""
                UPDATE payments SET status = 'REFUNDED', updated_at = now()
                WHERE trip_id = :tripId AND status = 'CAPTURED'""")
                .param("tripId", tripId)
                .update();
    }

    public Optional<String> statusOf(UUID tripId) {
        return jdbc.sql("SELECT status FROM payments WHERE trip_id = :tripId")
                .param("tripId", tripId)
                .query(String.class)
                .optional();
    }
}
