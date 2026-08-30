package dev.vlearning.orders.api;

import dev.vlearning.orders.order.OrderService;
import dev.vlearning.orders.order.PlaceOrderCommand;
import dev.vlearning.orders.pricing.OrderLine;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@RequestBody PlaceOrderRequest request) {
        var lines = request.lines().stream()
                .map(line -> new OrderLine(line.sku(), line.quantity(), line.unitPrice()))
                .toList();
        var order = orders.place(new PlaceOrderCommand(request.customerId(), lines,
                request.currency(), request.couponCode(), request.loyaltyMember(), request.cardToken()));
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.of(order));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable String id) {
        return OrderResponse.of(orders.get(id));
    }
}
