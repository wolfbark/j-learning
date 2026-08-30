package dev.vlearning.orders.order;

import java.util.List;

import dev.vlearning.orders.pricing.OrderLine;

public record PlaceOrderCommand(
        String customerId,
        List<OrderLine> lines,
        String currency,
        String couponCode,
        boolean loyaltyMember,
        String cardToken) {
}
