package dev.vlearning.reliability.settlement;

import java.util.Map;

import dev.vlearning.reliability.web.CorrelationIdFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SettlementController {

    private final SettlementService settlements;

    public SettlementController(SettlementService settlements) {
        this.settlements = settlements;
    }

    @PostMapping("/settlements")
    public SettlementService.SettlementResult settle(@RequestBody SettlementCommand command) {
        return settlements.settle(command);
    }

    @ExceptionHandler(SettlementFailedException.class)
    public ResponseEntity<Map<String, String>> failed(SettlementFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage(),
                        "correlationId", CorrelationIdFilter.current()));
    }
}
