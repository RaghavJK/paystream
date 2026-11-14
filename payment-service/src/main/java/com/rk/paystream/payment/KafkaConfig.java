package com.rk.paystream.payment;

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

    // Three partitions so you can observe key-based ordering and consumer-group rebalancing.
    @Bean
    NewTopic paymentsInitiated() {
        return TopicBuilder.name(Topics.INITIATED).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic paymentsScored() {
        return TopicBuilder.name(Topics.SCORED).partitions(PARTITIONS).replicas(1).build();
    }

    // The recoverer publishes to the SAME partition number, so the DLT needs as many partitions as its source.
    @Bean
    NewTopic paymentsScoredDlt() {
        return TopicBuilder.name(Topics.SCORED_DLT).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * Spring Boot applies a single CommonErrorHandler bean to every @KafkaListener container.
     *
     * A failing record is retried 3 times (0.5 s, 1 s, 2 s) and then published to "<topic>.DLT"
     * with the exception in its headers; the consumer then moves on, so one bad record cannot
     * block a partition. A record that cannot be parsed will never succeed, so it skips the retries.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
