package com.rk.paystream.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rk.paystream.payment.Events.PaymentScored;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ScoredListener {

    private static final Logger log = LoggerFactory.getLogger(ScoredListener.class);

    private final PaymentService service;
    private final ObjectMapper mapper;

    public ScoredListener(PaymentService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /**
     * At-least-once delivery: this method can be called twice for the same event.
     * applyScore is idempotent, so a replay is harmless.
     *
     * Any exception thrown here goes to the DefaultErrorHandler in KafkaConfig: retried with
     * backoff, then sent to payments.scored.DLT. An unparseable payload goes straight to the DLT.
     */
    @KafkaListener(topics = Topics.SCORED, groupId = "payment-service")
    public void onScored(String payload) throws JsonProcessingException {
        PaymentScored event = mapper.readValue(payload, PaymentScored.class);
        log.info("Payment {} scored {} -> {}", event.paymentId(), event.score(), event.decision());
        service.applyScore(event);
    }
}
