package dev.vlearning.orders.api;

import java.math.BigDecimal;

import dev.vlearning.orders.order.Order;

public record OrderResponse(
        String orderId,
        String status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shipping,
        BigDecimal total,
        String currency,
        String appliedRule,
        String paymentId,
        String declineReason) {

    public static OrderResponse of(Order order) {
        var quote = order.quote();
        return new OrderResponse(order.id(), order.status().name(),
                quote.subtotal(), quote.discount(), quote.shipping(), quote.total(),
                quote.currency(), quote.appliedRule(),
                order.paymentId(), order.declineReason());
    }
}
