package dev.vlearning.shipping.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The chaos switch: {@code POST /chaos {"mode":"SLOW_5S"}} changes how every
 * subsequent /shipments call behaves. Failure on demand — the only way to
 * practice distributed failure without waiting for production to provide it.
 */
@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    private final ChaosState state;

    public ChaosController(ChaosState state) {
        this.state = state;
    }

    public record ChaosRequest(ChaosMode mode) {
    }

    public record ChaosResponse(ChaosMode mode) {
    }

    @PostMapping
    public ChaosResponse set(@RequestBody ChaosRequest request) {
        if (request.mode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be one of OK, SLOW_5S, DOWN");
        }
        state.set(request.mode());
        log.info("chaos mode set to {}", request.mode());
        return new ChaosResponse(state.mode());
    }

    @GetMapping
    public ChaosResponse current() {
        return new ChaosResponse(state.mode());
    }
}
