package dev.vlearning.orders.shipping;

import java.util.UUID;

public record ShipmentResponse(String shipmentId, UUID orderId, String status) {
}
