package dev.vlearning.shipping.shipment;

import java.net.URI;
import java.util.Collection;
import java.util.UUID;

import dev.vlearning.shipping.chaos.ChaosState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private static final Logger log = LoggerFactory.getLogger(ShipmentController.class);

    private final ShipmentStore store;
    private final ChaosState chaos;

    public ShipmentController(ShipmentStore store, ChaosState chaos) {
        this.store = store;
        this.chaos = chaos;
    }

    public record ArrangeShipmentRequest(UUID orderId, String item, int quantity) {
    }

    @PostMapping
    public ResponseEntity<Shipment> arrange(@RequestBody ArrangeShipmentRequest request) {
        if (request.orderId() == null || request.item() == null || request.item().isBlank()
                || request.quantity() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "orderId, item and a quantity of at least 1 are required");
        }
        applyChaos();
        var shipment = store.save(Shipment.arranged(request.orderId(), request.item(), request.quantity()));
        log.info("shipment {} arranged for order {}", shipment.shipmentId(), shipment.orderId());
        return ResponseEntity.created(URI.create("/shipments/" + shipment.shipmentId())).body(shipment);
    }

    @GetMapping("/{id}")
    public Shipment get(@PathVariable String id) {
        return store.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such shipment"));
    }

    @GetMapping
    public Collection<Shipment> all() {
        return store.all();
    }

    private void applyChaos() {
        switch (chaos.mode()) {
            case OK -> { }
            case SLOW_5S -> sleepFiveSeconds();
            case DOWN -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "chaos: shipping is down");
        }
    }

    private void sleepFiveSeconds() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "interrupted mid-chaos");
        }
    }
}
