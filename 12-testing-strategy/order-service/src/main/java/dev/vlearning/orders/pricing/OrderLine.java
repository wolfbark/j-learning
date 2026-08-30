package dev.vlearning.orders.pricing;

import java.math.BigDecimal;

public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {
}
