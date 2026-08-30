package dev.vlearning.payments.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.vlearning.payments.domain.Payment;
import dev.vlearning.payments.domain.PaymentRepository;

/**
 * A hand-written test double — no mocking framework, no container. Fast enough
 * to run on every keystroke, and honest about what it cannot tell you: nothing
 * here proves the unique index exists (step 2's job).
 */
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<String, Payment> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByKey = new ConcurrentHashMap<>();

    @Override
    public boolean insertIfAbsent(Payment payment) {
        if (idByKey.putIfAbsent(payment.idempotencyKey(), payment.id()) != null) {
            return false;
        }
        byId.put(payment.id(), payment);
        return true;
    }

    @Override
    public Optional<Payment> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(idByKey.get(idempotencyKey)).map(byId::get);
    }
}
