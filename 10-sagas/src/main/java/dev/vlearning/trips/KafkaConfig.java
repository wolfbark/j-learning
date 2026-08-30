package dev.vlearning.trips;

import dev.vlearning.trips.messages.TripTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the four topics. Single partition per topic: per-trip ordering is
 * then trivial, which keeps the lesson about sagas, not about partitioning.
 */
@Configuration
class KafkaConfig {

    @Bean
    NewTopic eventsTopic(TripTopics topics) {
        return TopicBuilder.name(topics.events()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic flightCommandsTopic(TripTopics topics) {
        return TopicBuilder.name(topics.flightCommands()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic hotelCommandsTopic(TripTopics topics) {
        return TopicBuilder.name(topics.hotelCommands()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic paymentCommandsTopic(TripTopics topics) {
        return TopicBuilder.name(topics.paymentCommands()).partitions(1).replicas(1).build();
    }
}
