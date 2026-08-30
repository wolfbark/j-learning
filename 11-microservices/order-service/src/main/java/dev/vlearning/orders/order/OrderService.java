package dev.vlearning.orders.order;

import java.util.Optional;
import java.util.UUID;

import dev.vlearning.orders.shipping.ShipmentRequest;
import dev.vlearning.orders.shipping.ShippingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placing an order arranges shipping through the shipping-service — a
 * synchronous HTTP call, made while the customer waits.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final ShippingClient shipping;

    public OrderService(OrderRepository repository, ShippingClient shipping) {
        this.repository = repository;
        this.shipping = shipping;
    }

    public Order place(String customerId, String item, int quantity) {
        var order = Order.placed(customerId, item, quantity);
        repository.insert(order);

        var shipment = shipping.arrange(new ShipmentRequest(order.id(), item, quantity));
        repository.updateStatus(order.id(), OrderStatus.CONFIRMED, shipment.shipmentId());
        log.info("order {} confirmed, shipment {}", order.id(), shipment.shipmentId());

        return repository.findById(order.id()).orElseThrow();
    }

    public Optional<Order> find(UUID orderId) {
        return repository.findById(orderId);
    }
}
