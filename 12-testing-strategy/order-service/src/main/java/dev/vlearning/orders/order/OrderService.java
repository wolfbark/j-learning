package dev.vlearning.orders.order;

import java.time.Clock;
import java.util.UUID;

import dev.vlearning.orders.payments.PaymentPort;
import dev.vlearning.orders.pricing.OrderPricer;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderPricer pricer;
    private final PaymentPort payments;
    private final OrderRepository repository;
    private final Clock clock;

    public OrderService(OrderPricer pricer, PaymentPort payments, OrderRepository repository, Clock clock) {
        this.pricer = pricer;
        this.payments = payments;
        this.repository = repository;
        this.clock = clock;
    }

    public Order place(PlaceOrderCommand command) {
        var quote = pricer.quote(command.lines(), command.currency(),
                command.couponCode(), command.loyaltyMember());

        String orderId = UUID.randomUUID().toString();
        // The order id doubles as the idempotency key: a retried place() for the
        // same order must never authorize twice.
        var outcome = payments.authorize(orderId, orderId, quote.total(),
                quote.currency(), command.cardToken());

        var order = new Order(orderId, command.customerId(), quote,
                outcome.approved() ? OrderStatus.PAID : OrderStatus.PAYMENT_DECLINED,
                outcome.paymentId(), outcome.declineReason(), clock.instant());
        repository.save(order);
        return order;
    }

    public Order get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no order with id " + id));
    }
}
