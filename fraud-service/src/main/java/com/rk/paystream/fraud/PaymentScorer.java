package com.rk.paystream.fraud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rk.paystream.fraud.Events.PaymentInitiated;
import com.rk.paystream.fraud.Events.PaymentScored;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Consume, score, store, publish.
 *
 * Reliability without an outbox: the listener only returns normally after the broker has
 * acknowledged the score. If the save or the publish fails, the method throws, the offset is not
 * committed, and the record is retried and finally dead-lettered (see KafkaConfig). Because the
 * save is an upsert by payment ID and the consumer of payments.scored is idempotent, repeating
 * the whole method is safe. That gives at-least-once end to end.
 *
 * Start a second instance of this service and watch the three partitions being shared between
 * the two consumers (a rebalance).
 */
@Component
public class PaymentScorer {

    private static final Logger log = LoggerFactory.getLogger(PaymentScorer.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final FraudRules rules;
    private final RiskAssessmentRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public PaymentScorer(FraudRules rules, RiskAssessmentRepository repository,
                         KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.rules = rules;
        this.repository = repository;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Events.TOPIC_INITIATED, groupId = "fraud-service")
    public void onPaymentInitiated(String payload) throws JsonProcessingException {
        PaymentInitiated event = validated(mapper.readValue(payload, PaymentInitiated.class));
        FraudRules.Result result = rules.score(event);
        Instant now = Instant.now();
        String paymentId = event.paymentId().toString();

        repository.save(new RiskAssessment(paymentId, event.debtorAccount(), event.occurredAt(),
                result.score(), result.decision(), result.reason(), result.hits(), now));

        PaymentScored scored = new PaymentScored(event.paymentId(), result.score(), result.decision(),
                result.reason(), now);
        publish(paymentId, mapper.writeValueAsString(scored));
        log.info("Scored payment {}: {} ({})", paymentId, result.score(), result.decision());
    }

    private static PaymentInitiated validated(PaymentInitiated event) {
        if (event == null || event.paymentId() == null || event.debtorAccount() == null
                || event.creditorAccount() == null || event.amount() == null || event.occurredAt() == null) {
            throw new InvalidEventException("PaymentInitiated is missing a required field");
        }
        return event;
    }

    /** Blocks until the broker acknowledges, and fails loudly so the record is retried. */
    private void publish(String key, String json) {
        try {
            kafka.send(Events.TOPIC_SCORED, key, json).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing the score for " + key, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Score for " + key + " was not published; the record will be retried", e);
        }
    }
}
