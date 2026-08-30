package dev.vlearning.orders.api;

import java.math.BigDecimal;
import java.util.List;

public record PlaceOrderRequest(
        String customerId,
        List<Line> lines,
        String currency,
        String couponCode,
        boolean loyaltyMember,
        String cardToken) {

    public record Line(String sku, int quantity, BigDecimal unitPrice) {
    }
}
