package dev.vlearning.trips.messages;

/**
 * The only doorway between the simulated services. Implemented by
 * {@code KafkaMessageBus} in production code and by plain Mockito mocks in the
 * participant unit tests. Messages are keyed by {@code tripId}, so everything
 * about one trip stays ordered within a partition.
 */
public interface MessageBus {

    void publish(String topic, TripMessage message);
}
