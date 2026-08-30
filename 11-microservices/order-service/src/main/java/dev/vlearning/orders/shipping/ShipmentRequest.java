package dev.vlearning.orders.shipping;

import java.util.UUID;

public record ShipmentRequest(UUID orderId, String item, int quantity) {
}
