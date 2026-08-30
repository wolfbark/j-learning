package dev.vlearning.trips.chaos;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Failure injection over HTTP, for manual runs:
 *
 * <pre>
 * curl -X POST localhost:8080/chaos/hotel/fail-next     # next ReserveHotel → HotelRejected
 * curl -X POST localhost:8080/chaos/hotel/drop-next     # next hotel command vanishes silently
 * curl -X DELETE localhost:8080/chaos                   # calm everything down
 * </pre>
 */
@RestController
@RequestMapping("/chaos")
class ChaosController {

    private final ChaosToggles toggles;

    ChaosController(ChaosToggles toggles) {
        this.toggles = toggles;
    }

    @PostMapping("/{service}/fail-next")
    ResponseEntity<Map<String, String>> failNext(@PathVariable String service) {
        if (!ChaosToggles.SERVICES.contains(service)) {
            return ResponseEntity.notFound().build();
        }
        toggles.failNext(service);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("service", service, "chaos", "next request will be rejected"));
    }

    @PostMapping("/{service}/drop-next")
    ResponseEntity<Map<String, String>> dropNext(@PathVariable String service) {
        if (!ChaosToggles.SERVICES.contains(service)) {
            return ResponseEntity.notFound().build();
        }
        toggles.dropNext(service);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("service", service, "chaos", "next command will be silently dropped"));
    }

    @DeleteMapping
    ResponseEntity<Map<String, String>> reset() {
        toggles.reset();
        return ResponseEntity.ok(Map.of("chaos", "reset"));
    }
}
