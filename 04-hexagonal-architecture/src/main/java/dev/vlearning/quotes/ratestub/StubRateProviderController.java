package dev.vlearning.quotes.ratestub;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plays the role of a REMOTE rate provider so the project runs standalone.
 * Treat everything in this package as if it lived on another team's server:
 * it is outside the hexagon and stays untouched for the whole lesson.
 */
@RestController
@RequestMapping("/external/rates")
public class StubRateProviderController {

    private static final Map<String, String> BASE_RATES = Map.of(
            "AUTO", "90.00",
            "HOME", "45.50",
            "LIFE", "120.00");

    public record RateResponse(String productCode, BigDecimal baseRate) {
    }

    @GetMapping("/{productCode}")
    public ResponseEntity<RateResponse> baseRate(@PathVariable String productCode) throws InterruptedException {
        Thread.sleep(50); // simulated network latency — you will feel this in step 5
        String rate = BASE_RATES.get(productCode);
        if (rate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new RateResponse(productCode, new BigDecimal(rate)));
    }
}
