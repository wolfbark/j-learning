package com.vlearning.bdd.web;

import com.vlearning.bdd.pricing.Member;
import com.vlearning.bdd.pricing.MemberTier;
import com.vlearning.bdd.pricing.Order;
import com.vlearning.bdd.pricing.PricingResult;
import com.vlearning.bdd.pricing.PricingService;
import com.vlearning.bdd.shipping.ShippingQuote;
import com.vlearning.bdd.shipping.ShippingService;
import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP edge. Deliberately thin: it maps JSON to the domain API and back, and that is
 * all it is allowed to do -- which is why the feature files bind to the domain instead of
 * to this class, and why one narrow test (CheckoutControllerTest) is enough coverage here.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final ShippingService shippingService;
    private final PricingService pricingService;

    public CheckoutController(ShippingService shippingService, PricingService pricingService) {
        this.shippingService = shippingService;
        this.pricingService = pricingService;
    }

    @PostMapping("/shipping-quote")
    public ShippingQuoteResponse shippingQuote(@RequestBody ShippingQuoteRequest request) {
        ShippingQuote quote = shippingService.quoteFor(request.amountCharged(), request.tier());
        return new ShippingQuoteResponse(quote.cost().toPlainString(), quote.reason().name());
    }

    @PostMapping("/price")
    public ResponseEntity<?> price(@RequestBody PriceRequest request) {
        Order order = Order.of(request.subtotal())
                .withPromoCode(request.promoCode())
                .payingWithPoints(request.pointsToRedeem());
        try {
            PricingResult result = pricingService.price(order, new Member(request.tier(), request.pointBalance()));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException rejected) {
            return ResponseEntity.badRequest().body(rejected.getMessage());
        }
    }

    public record ShippingQuoteRequest(BigDecimal amountCharged, MemberTier tier) {
    }

    public record ShippingQuoteResponse(String cost, String reason) {
    }

    public record PriceRequest(BigDecimal subtotal, String promoCode, int pointsToRedeem,
                               MemberTier tier, int pointBalance) {
    }
}
