package dev.vlearning.orders;

import dev.vlearning.orders.chaos.ChaosKafkaTemplate;
import dev.vlearning.orders.chaos.ChaosMonkey;
import dev.vlearning.orders.order.OrderPlaced;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.RecordMessageConverter;

@Configuration
class KafkaConfig {

    @Bean
    NewTopic orderPlacedTopic() {
        return TopicBuilder.name(OrderPlaced.TOPIC).partitions(1).replicas(1).build();
    }

    /**
     * Replaces Boot's auto-configured KafkaTemplate with the chaos-aware one.
     * Values travel as raw JSON bytes; the {@link RecordMessageConverter}
     * (provided by spring-modulith-events-kafka) turns objects into JSON on
     * {@code send(Message)} and JSON back into listener arguments on receive.
     */
    @Bean
    ChaosKafkaTemplate kafkaTemplate(ProducerFactory<String, byte[]> producerFactory, ChaosMonkey chaos,
            ObjectProvider<RecordMessageConverter> messageConverter) {
        var template = new ChaosKafkaTemplate(producerFactory, chaos);
        messageConverter.ifUnique(template::setMessageConverter);
        return template;
    }
}
