package dev.vlearning.orders.order;

import java.time.Instant;

import dev.vlearning.orders.pricing.PriceQuote;

public record Order(
        String id,
        String customerId,
        PriceQuote quote,
        OrderStatus status,
        String paymentId,
        String declineReason,
        Instant placedAt) {
}
