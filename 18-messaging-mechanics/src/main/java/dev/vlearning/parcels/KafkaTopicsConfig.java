package dev.vlearning.parcels;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfig {

    @Bean
    NewTopic scansTopic(ParcelsProperties properties) {
        return TopicBuilder.name(properties.topics().scans())
                .partitions(properties.scanPartitions())
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic notifyTopic(ParcelsProperties properties) {
        return TopicBuilder.name(properties.topics().tasks()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic notifyDlqTopic(ParcelsProperties properties) {
        return TopicBuilder.name(properties.topics().dlq()).partitions(1).replicas(1).build();
    }
}
