package dev.vlearning.orders.order;

import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcClient jdbc;

    OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Order order) {
        jdbc.sql("INSERT INTO orders (id, customer, total, placed_at) VALUES (:id, :customer, :total, :placedAt)")
                .param("id", order.id())
                .param("customer", order.customer())
                .param("total", order.total())
                .param("placedAt", order.placedAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    public boolean exists(UUID id) {
        return jdbc.sql("SELECT count(*) FROM orders WHERE id = :id")
                .param("id", id)
                .query(Long.class)
                .single() > 0;
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single();
    }
}
