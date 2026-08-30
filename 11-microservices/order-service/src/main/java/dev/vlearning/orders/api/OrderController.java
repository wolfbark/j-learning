package dev.vlearning.orders.api;

import java.net.URI;
import java.util.UUID;

import dev.vlearning.orders.order.Order;
import dev.vlearning.orders.order.OrderService;
import dev.vlearning.orders.order.OrderStatus;
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
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    public record PlaceOrderRequest(String customerId, String item, int quantity) {
    }

    public record OrderResponse(UUID orderId, String customerId, String item, int quantity,
                                OrderStatus status, String shipmentId) {

        static OrderResponse of(Order order) {
            return new OrderResponse(order.id(), order.customerId(), order.item(),
                    order.quantity(), order.status(), order.shipmentId());
        }
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@RequestBody PlaceOrderRequest request) {
        if (request.customerId() == null || request.customerId().isBlank()
                || request.item() == null || request.item().isBlank()
                || request.quantity() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "customerId, item and a quantity of at least 1 are required");
        }
        var order = orders.place(request.customerId(), request.item(), request.quantity());
        return ResponseEntity.created(URI.create("/orders/" + order.id()))
                .body(OrderResponse.of(order));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.find(id)
                .map(OrderResponse::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such order"));
    }
}
