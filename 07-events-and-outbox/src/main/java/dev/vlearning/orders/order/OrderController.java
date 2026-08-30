package dev.vlearning.orders.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    record ItemRequest(String sku, int quantity, BigDecimal unitPrice) {
    }

    record PlaceOrderRequest(String customer, List<ItemRequest> items) {
    }

    record PlaceOrderResponse(UUID orderId) {
    }

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    PlaceOrderResponse place(@RequestBody PlaceOrderRequest request) {
        var items = request.items() == null ? List.<OrderItem>of()
                : request.items().stream()
                        .map(item -> new OrderItem(item.sku(), item.quantity(), item.unitPrice()))
                        .toList();
        return new PlaceOrderResponse(orderService.place(request.customer(), items));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String badRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
