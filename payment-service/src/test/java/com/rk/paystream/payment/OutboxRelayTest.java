package com.rk.paystream.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private OutboxRepository outbox;
    private KafkaTemplate<String, String> kafka;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        outbox = mock(OutboxRepository.class);
        kafka = mock(KafkaTemplate.class);
        relay = new OutboxRelay(outbox, kafka, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void publishesPendingRowsWithTheAggregateIdAsKeyAndMarksThemSent() {
        OutboxEvent first = OutboxEvent.pending(Topics.INITIATED, "p-1", "{\"n\":1}");
        OutboxEvent second = OutboxEvent.pending(Topics.INITIATED, "p-2", "{\"n\":2}");
        when(outbox.lockNextBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        verify(kafka).send(Topics.INITIATED, "p-1", "{\"n\":1}");
        verify(kafka).send(Topics.INITIATED, "p-2", "{\"n\":2}");
        assertThat(first.getSentAt()).isNotNull();
        assertThat(second.getSentAt()).isNotNull();
    }

    @Test
    void stopsAtTheFirstFailureSoLaterRowsAreNotPublishedOutOfOrder() {
        OutboxEvent first = OutboxEvent.pending(Topics.INITIATED, "p-1", "{}");
        OutboxEvent second = OutboxEvent.pending(Topics.INITIATED, "p-2", "{}");
        when(outbox.lockNextBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), eq("p-1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.publishPending();

        assertThat(first.getSentAt()).isNull();
        assertThat(second.getSentAt()).isNull();
        verify(kafka, never()).send(Topics.INITIATED, "p-2", "{}");
    }
}
