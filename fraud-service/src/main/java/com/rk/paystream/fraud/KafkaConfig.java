package com.rk.paystream.fraud;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;

    // This service produces payments.scored, so it declares it. payment-service declares it too;
    // declaring an existing topic with the same settings is harmless.
    @Bean
    NewTopic paymentsScored() {
        return TopicBuilder.name(Events.TOPIC_SCORED).partitions(PARTITIONS).replicas(1).build();
    }

    // Same partition count as payments.initiated: the recoverer publishes to the same partition number.
    @Bean
    NewTopic paymentsInitiatedDlt() {
        return TopicBuilder.name(Events.TOPIC_INITIATED_DLT).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * Retry 3 times (0.5 s, 1 s, 2 s), then publish the record to payments.initiated.DLT and move on.
     * Records that can never succeed (bad JSON, missing fields) skip the retries.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, InvalidEventException.class);
        return handler;
    }
}
