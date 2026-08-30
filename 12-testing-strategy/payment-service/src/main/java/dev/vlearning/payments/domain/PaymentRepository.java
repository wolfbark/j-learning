package dev.vlearning.payments.domain;

import java.util.Optional;

public interface PaymentRepository {

    /** @return false if the idempotency key was already taken (unique-index violation). */
    boolean insertIfAbsent(Payment payment);

    Optional<Payment> findById(String id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
