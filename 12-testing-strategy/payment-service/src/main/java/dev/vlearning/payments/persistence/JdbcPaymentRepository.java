package dev.vlearning.payments.persistence;

import java.util.Optional;

import dev.vlearning.payments.domain.Currencies;
import dev.vlearning.payments.domain.Payment;
import dev.vlearning.payments.domain.PaymentRepository;
import dev.vlearning.payments.domain.PaymentStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private final JdbcClient jdbc;

    public JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(Payment payment) {
        try {
            jdbc.sql("""
                    INSERT INTO payments (id, order_id, amount, currency, status,
                                          decline_reason, idempotency_key, created_at)
                    VALUES (:id, :orderId, :amount, :currency, :status,
                            :declineReason, :idempotencyKey, :createdAt)
                    """)
                    .param("id", payment.id())
                    .param("orderId", payment.orderId())
                    .param("amount", payment.amount())
                    .param("currency", payment.currency())
                    .param("status", payment.status().name())
                    .param("declineReason", payment.declineReason())
                    .param("idempotencyKey", payment.idempotencyKey())
                    .param("createdAt", java.sql.Timestamp.from(payment.createdAt()))
                    .update();
            return true;
        }
        catch (DuplicateKeyException e) {
            // Someone else won the race on this idempotency key. Not an error:
            // the caller re-reads and returns the winner's payment.
            return false;
        }
    }

    @Override
    public Optional<Payment> findById(String id) {
        return jdbc.sql("SELECT * FROM payments WHERE id = :id")
                .param("id", id)
                .query(JdbcPaymentRepository::toPayment)
                .optional();
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("SELECT * FROM payments WHERE idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(JdbcPaymentRepository::toPayment)
                .optional();
    }

    private static Payment toPayment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String currency = rs.getString("currency");
        return new Payment(
                rs.getString("id"),
                rs.getString("order_id"),
                // numeric(19,4) comes back at scale 4; money is exposed at the currency's scale.
                Currencies.normalize(rs.getBigDecimal("amount").stripTrailingZeros(), currency),
                currency,
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getString("decline_reason"),
                rs.getString("idempotency_key"),
                rs.getTimestamp("created_at").toInstant());
    }
}
