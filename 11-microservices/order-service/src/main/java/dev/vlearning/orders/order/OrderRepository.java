package dev.vlearning.orders.order;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Order order) {
        jdbc.sql("""
                INSERT INTO orders (id, customer_id, item, quantity, status, shipment_id, placed_at)
                VALUES (:id, :customerId, :item, :quantity, :status, :shipmentId, :placedAt)
                """)
                .param("id", order.id())
                .param("customerId", order.customerId())
                .param("item", order.item())
                .param("quantity", order.quantity())
                .param("status", order.status().name())
                .param("shipmentId", order.shipmentId())
                .param("placedAt", Timestamp.from(order.placedAt()))
                .update();
    }

    public void updateStatus(UUID orderId, OrderStatus status, String shipmentId) {
        jdbc.sql("UPDATE orders SET status = :status, shipment_id = :shipmentId WHERE id = :id")
                .param("id", orderId)
                .param("status", status.name())
                .param("shipmentId", shipmentId)
                .update();
    }

    public Optional<Order> findById(UUID orderId) {
        return jdbc.sql("SELECT * FROM orders WHERE id = :id")
                .param("id", orderId)
                .query((rs, rowNum) -> new Order(
                        rs.getObject("id", UUID.class),
                        rs.getString("customer_id"),
                        rs.getString("item"),
                        rs.getInt("quantity"),
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getString("shipment_id"),
                        rs.getTimestamp("placed_at").toInstant()))
                .optional();
    }
}
