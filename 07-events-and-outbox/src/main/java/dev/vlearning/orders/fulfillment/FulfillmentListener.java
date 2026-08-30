package dev.vlearning.orders.fulfillment;

import dev.vlearning.orders.order.OrderPlaced;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fulfillment side: reacts to OrderPlaced announcements from the broker.
 * In production this would be a separate deployable; here it lives in the same
 * JVM so one test run can observe both ends of the pipeline.
 */
@Component
class FulfillmentListener {

    private final FulfillmentRepository fulfillment;

    FulfillmentListener(FulfillmentRepository fulfillment) {
        this.fulfillment = fulfillment;
    }

    @KafkaListener(topics = OrderPlaced.TOPIC)
    @Transactional
    void on(OrderPlaced event) {
        fulfillment.recordTask(event);
    }
}
