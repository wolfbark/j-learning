package dev.vlearning.orders.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Pure pricing arithmetic: no Spring, no I/O, no clock. Tiered discounts, a
 * loyalty bonus with a cap, a coupon that loses to a better tier, currency-aware
 * rounding, and free shipping above a threshold.
 *
 * <p>Every one of those rules is a boundary, and boundaries are where both bugs
 * and surviving mutants live. Step 7 is about that.
 */
@Component
public class OrderPricer {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Discount tier thresholds per currency: {5% from, 10% from, 15% from}. */
    private static final Map<String, BigDecimal[]> TIERS = Map.of(
            "USD", new BigDecimal[] {new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("1000.00")},
            "EUR", new BigDecimal[] {new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("1000.00")},
            "JPY", new BigDecimal[] {new BigDecimal("15000"), new BigDecimal("75000"), new BigDecimal("150000")});

    private static final Map<String, BigDecimal> FREE_SHIPPING_FROM = Map.of(
            "USD", new BigDecimal("100.00"),
            "EUR", new BigDecimal("100.00"),
            "JPY", new BigDecimal("15000"));

    private static final Map<String, BigDecimal> FLAT_SHIPPING = Map.of(
            "USD", new BigDecimal("4.99"),
            "EUR", new BigDecimal("4.49"),
            "JPY", new BigDecimal("500"));

    private static final Map<String, Integer> MINOR_UNITS = Map.of("USD", 2, "EUR", 2, "JPY", 0);

    private static final int LOYALTY_BONUS_PERCENT = 2;
    private static final int MAX_DISCOUNT_PERCENT = 15;
    private static final String WELCOME_COUPON = "WELCOME10";
    private static final int WELCOME_COUPON_PERCENT = 10;

    public PriceQuote quote(List<OrderLine> lines, String currency, String couponCode, boolean loyaltyMember) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one line");
        }
        int scale = minorUnits(currency);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + line.sku());
            }
            if (line.unitPrice() == null || line.unitPrice().signum() < 0) {
                throw new IllegalArgumentException("unit price must not be negative: " + line.sku());
            }
            subtotal = subtotal.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        subtotal = subtotal.setScale(scale, RoundingMode.HALF_UP);

        int tierPercent = tierPercent(subtotal, currency);
        int percent = tierPercent;
        String rule = tierPercent == 0 ? "NO_DISCOUNT" : "TIER_" + tierPercent;

        if (WELCOME_COUPON.equals(couponCode) && tierPercent == 0) {
            // The welcome coupon is for small first orders; a real tier discount beats it,
            // and when it does the coupon is silently dropped.
            percent = WELCOME_COUPON_PERCENT;
            rule = "COUPON_" + WELCOME_COUPON;
        }
        if (loyaltyMember) {
            percent = Math.min(percent + LOYALTY_BONUS_PERCENT, MAX_DISCOUNT_PERCENT);
            rule = rule + "+LOYALTY";
        }

        BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(percent))
                .divide(HUNDRED)
                .setScale(scale, RoundingMode.HALF_UP);
        BigDecimal discounted = subtotal.subtract(discount);
        BigDecimal shipping = discounted.compareTo(FREE_SHIPPING_FROM.get(currency)) >= 0
                ? BigDecimal.ZERO.setScale(scale)
                : FLAT_SHIPPING.get(currency).setScale(scale, RoundingMode.HALF_UP);

        return new PriceQuote(subtotal, discount, shipping, discounted.add(shipping), currency, rule);
    }

    private static int tierPercent(BigDecimal subtotal, String currency) {
        BigDecimal[] tiers = TIERS.get(currency);
        if (subtotal.compareTo(tiers[2]) >= 0) {
            return 15;
        }
        if (subtotal.compareTo(tiers[1]) >= 0) {
            return 10;
        }
        if (subtotal.compareTo(tiers[0]) >= 0) {
            return 5;
        }
        return 0;
    }

    private static int minorUnits(String currency) {
        Integer units = MINOR_UNITS.get(currency);
        if (units == null) {
            throw new IllegalArgumentException("unsupported currency: " + currency);
        }
        return units;
    }
}
