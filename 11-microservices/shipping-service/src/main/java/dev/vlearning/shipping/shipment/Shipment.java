package dev.vlearning.shipping.shipment;

import java.time.Instant;
import java.util.UUID;

public record Shipment(String shipmentId, UUID orderId, String item, int quantity, String status,
                       Instant arrangedAt) {

    public static Shipment arranged(UUID orderId, String item, int quantity) {
        var id = "SHP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Shipment(id, orderId, item, quantity, "ARRANGED", Instant.now());
    }
}
