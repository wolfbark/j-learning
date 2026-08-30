package dev.vlearning.parcels.support;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** One RabbitMQ broker for the whole suite. Testcontainers 2.x: {@code org.testcontainers.rabbitmq}. */
public final class RabbitSupport {

    public static final String IMAGE = "rabbitmq:3-management-alpine";

    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(IMAGE);

    static {
        RABBIT.start();
    }

    private RabbitSupport() {
    }

    public static String amqpUrl() {
        return RABBIT.getAmqpUrl();
    }

    public static Connection connect() {
        try {
            var factory = new ConnectionFactory();
            factory.setUri(amqpUrl());
            return factory.newConnection();
        } catch (Exception e) {
            throw new IllegalStateException("could not connect to RabbitMQ", e);
        }
    }
}
