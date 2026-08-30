package dev.vlearning.shipping.shipment;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory shipment store. Deliberately not a database: this service's
 * persistence is not what the lesson is about, and losing shipments on
 * restart is itself a talking point in step 4.
 */
@Component
public class ShipmentStore {

    private final Map<String, Shipment> shipments = new ConcurrentHashMap<>();

    public Shipment save(Shipment shipment) {
        shipments.put(shipment.shipmentId(), shipment);
        return shipment;
    }

    public Optional<Shipment> find(String shipmentId) {
        return Optional.ofNullable(shipments.get(shipmentId));
    }

    public Optional<Shipment> findByOrderId(UUID orderId) {
        return shipments.values().stream()
                .filter(shipment -> shipment.orderId().equals(orderId))
                .findFirst();
    }

    public Collection<Shipment> all() {
        return shipments.values();
    }

    public void clear() {
        shipments.clear();
    }
}
