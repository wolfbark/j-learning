package dev.vlearning.orders.order;

import java.util.List;
import java.util.UUID;

import dev.vlearning.orders.chaos.ChaosMonkey;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

/**
 * The naive dual write. One business operation, two independent systems of
 * record: a Postgres transaction and a Kafka producer. The switch below is not
 * three different designs — it is the SAME design with the send moved around,
 * which is exactly what teams do when they first notice the problem. Step 2
 * proves that every arm loses. Step 3 replaces all of it.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final KafkaTemplate<String, byte[]> kafka;
    private final TransactionTemplate transactions;
    private final ChaosMonkey chaos;
    private final JsonMapper json;

    OrderService(OrderRepository orders, KafkaTemplate<String, byte[]> kafka,
            PlatformTransactionManager transactionManager, ChaosMonkey chaos, JsonMapper json) {
        this.orders = orders;
        this.kafka = kafka;
        this.transactions = new TransactionTemplate(transactionManager);
        this.chaos = chaos;
        this.json = json;
    }

    public UUID place(String customer, List<OrderItem> items) {
        var order = Order.place(customer, items);
        var event = OrderPlaced.from(order);

        switch (chaos.crashPoint()) {
            case NONE -> {
                transactions.executeWithoutResult(tx -> orders.insert(order));
                sendDirectly(event);
            }
            case AFTER_COMMIT_BEFORE_SEND -> {
                transactions.executeWithoutResult(tx -> orders.insert(order));
                chaos.crashNow();
                sendDirectly(event);
            }
            case AFTER_SEND_BEFORE_COMMIT -> transactions.executeWithoutResult(tx -> {
                orders.insert(order);
                sendDirectly(event);
                chaos.crashNow();
            });
        }
        return order.id();
    }

    private void sendDirectly(OrderPlaced event) {
        kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), json.writeValueAsBytes(event));
    }
}
