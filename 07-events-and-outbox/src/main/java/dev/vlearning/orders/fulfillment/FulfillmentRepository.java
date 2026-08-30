package dev.vlearning.orders.fulfillment;

import java.util.UUID;

import dev.vlearning.orders.order.OrderPlaced;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FulfillmentRepository {

    private final JdbcClient jdbc;

    FulfillmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void recordTask(OrderPlaced event) {
        jdbc.sql("INSERT INTO fulfillment_tasks (order_id, customer) VALUES (:orderId, :customer)")
                .param("orderId", event.orderId())
                .param("customer", event.customer())
                .update();
    }

    public long countTasksFor(UUID orderId) {
        return jdbc.sql("SELECT count(*) FROM fulfillment_tasks WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query(Long.class)
                .single();
    }
}
