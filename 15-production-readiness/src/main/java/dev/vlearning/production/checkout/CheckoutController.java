package dev.vlearning.production.checkout;

import java.util.Map;

import dev.vlearning.production.gateway.GatewayMeter;
import dev.vlearning.production.gateway.PaymentGateway.GatewayException;
import dev.vlearning.production.gateway.PaymentGateway.GatewayUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {

    private final CheckoutService checkout;
    private final GatewayMeter meter;

    public CheckoutController(CheckoutService checkout, GatewayMeter meter) {
        this.checkout = checkout;
        this.meter = meter;
    }

    @PostMapping("/checkout")
    public CheckoutService.CheckoutResult checkout(@RequestBody CheckoutRequest request) {
        return checkout.checkout(request.orderId(), request.amountCents());
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics() {
        return Map.of(
                "gatewayCalls", meter.calls(),
                "gatewayPeakInFlight", meter.peakInFlight());
    }

    @PostMapping("/diagnostics/reset")
    public Map<String, String> reset() {
        meter.reset();
        return Map.of("status", "reset");
    }

    /** A dependency failure is a 502, not a 500: it is not our bug. */
    @ExceptionHandler(GatewayException.class)
    ProblemDetail gatewayFailed(GatewayException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "payment gateway failed");
    }

    /** The breaker is open: we did not even try. Fast, cheap, and honest. */
    @ExceptionHandler(GatewayUnavailableException.class)
    ProblemDetail gatewayUnavailable(GatewayUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    public record CheckoutRequest(String orderId, long amountCents) {}
}
