package dev.vlearning.orders.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import dev.vlearning.orders.pricing.OrderLine;
import dev.vlearning.orders.pricing.OrderPricer;
import dev.vlearning.orders.support.StubPaymentPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIVEN unit tests for the order flow, with the payment boundary stubbed.
 */
class PlaceOrderTest {

    private final StubPaymentPort payments = new StubPaymentPort();
    private final OrderRepository repository = new OrderRepository();
    private final OrderService service = new OrderService(new OrderPricer(), payments, repository,
            Clock.fixed(Instant.parse("2026-08-25T10:15:30Z"), ZoneOffset.UTC));

    private static PlaceOrderCommand command() {
        return new PlaceOrderCommand("cust-1",
                List.of(new OrderLine("SKU-1", 2, new BigDecimal("125.00"))),
                "USD", null, false, "tok_visa_ok");
    }

    @Test
    void anApprovedOrderIsPaidAndCarriesThePaymentId() {
        var order = service.place(command());

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.paymentId()).isEqualTo("pay_stub");
        assertThat(order.quote().total()).isEqualByComparingTo("237.50");
        assertThat(repository.findById(order.id())).contains(order);
    }

    @Test
    void aDeclinedOrderKeepsTheReasonRatherThanFailing() {
        payments.willDecline("CARD_DECLINED");

        var order = service.place(command());

        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
        assertThat(order.declineReason()).isEqualTo("CARD_DECLINED");
    }

    @Test
    void theOrderIdIsSentAsTheIdempotencyKeyAndTheAuthorizedAmountIsTheTotal() {
        var order = service.place(command());

        assertThat(payments.calls).hasSize(1);
        assertThat(payments.calls.getFirst().idempotencyKey()).isEqualTo(order.id());
        assertThat(payments.calls.getFirst().amount()).isEqualByComparingTo("237.50");
    }
}
