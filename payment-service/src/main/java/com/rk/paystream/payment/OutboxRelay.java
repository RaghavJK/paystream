package com.rk.paystream.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polling publisher for the transactional outbox.
 *
 * Guarantee: at-least-once. If the broker acknowledges a record and the database commit then
 * fails, the row stays unsent and is published again on the next poll. Consumers must be idempotent.
 *
 * Ordering: rows are published in id order and the batch stops at the first failure, so events
 * for one payment are never reordered by this relay.
 *
 * TODO (your exercise): delete or archive sent rows older than N days, and expose the count of
 * unsent rows as a Micrometer gauge so a stuck relay raises an alert.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int BATCH_SIZE = 50;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${paystream.outbox.poll-ms:500}")
    public void publishPending() {
        tx.executeWithoutResult(status -> relayBatch());
    }

    /** Runs inside the transaction opened by publishPending. Returns how many rows were published. */
    int relayBatch() {
        List<OutboxEvent> batch = outbox.lockNextBatch(BATCH_SIZE);
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                // Block for the broker's acknowledgement: a row is only marked sent once Kafka has it.
                kafka.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markSent();
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException | TimeoutException e) {
                log.warn("Outbox row {} not published, will retry on the next poll: {}", event.getId(), e.toString());
                break;
            }
        }
        return sent;
    }
}
