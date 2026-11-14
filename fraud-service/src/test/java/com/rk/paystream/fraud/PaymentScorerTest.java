package com.rk.paystream.fraud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rk.paystream.fraud.Events.PaymentInitiated;
import com.rk.paystream.fraud.rules.FraudRule;
import com.rk.paystream.fraud.rules.HighAmountRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentScorerTest {

    private static final FraudProperties PROPS = new FraudProperties(
            50, new BigDecimal("100000"), new BigDecimal("10000"), List.of("WATCH"), 60, 4);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private RiskAssessmentRepository repository;
    private KafkaTemplate<String, String> kafka;
    private PaymentScorer scorer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(RiskAssessmentRepository.class);
        kafka = mock(KafkaTemplate.class);
        List<FraudRule> oneRule = List.of(new HighAmountRule(PROPS));
        scorer = new PaymentScorer(new FraudRules(oneRule, PROPS), repository, kafka, mapper);
    }

    private String event(UUID id, String amount) throws JsonProcessingException {
        return mapper.writeValueAsString(
                new PaymentInitiated(id, "ACC-1", "ACC-2", new BigDecimal(amount), "INR", Instant.now()));
    }

    @Test
    void savesTheAssessmentThenPublishesTheScoreKeyedByPaymentId() throws Exception {
        UUID id = UUID.randomUUID();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        scorer.onPaymentInitiated(event(id, "250001"));

        ArgumentCaptor<RiskAssessment> saved = ArgumentCaptor.forClass(RiskAssessment.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(repository, kafka);
        order.verify(repository).save(saved.capture());
        order.verify(kafka).send(eq(Events.TOPIC_SCORED), eq(id.toString()), payload.capture());

        assertThat(saved.getValue().paymentId()).isEqualTo(id.toString());
        assertThat(saved.getValue().debtorAccount()).isEqualTo("ACC-1");
        assertThat(saved.getValue().decision()).isEqualTo(FraudRules.FLAGGED);
        assertThat(saved.getValue().hits()).hasSize(1);
        assertThat(payload.getValue()).contains(id.toString()).contains("FLAGGED");
    }

    @Test
    void aFailedPublishThrowsSoTheRecordIsRetriedInsteadOfSilentlyLost() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        String payload = event(UUID.randomUUID(), "10");

        assertThatThrownBy(() -> scorer.onPaymentInitiated(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was not published");
    }

    @Test
    void anEventWithMissingFieldsIsRejectedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> scorer.onPaymentInitiated("{\"currency\":\"INR\"}"))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(repository, kafka);
    }

    @Test
    void unparseableJsonIsAJsonProcessingException() {
        assertThatThrownBy(() -> scorer.onPaymentInitiated("not json"))
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(repository, kafka);
    }
}
