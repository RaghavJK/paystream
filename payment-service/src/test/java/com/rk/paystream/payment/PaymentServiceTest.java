package com.rk.paystream.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("250.00");

    private PaymentRepository payments;
    private OutboxRepository outbox;
    private IdempotencyRepository idempotency;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        outbox = mock(OutboxRepository.class);
        idempotency = mock(IdempotencyRepository.class);
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        // A TransactionTemplate over a mock manager simply runs the callback.
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new PaymentService(payments, outbox, idempotency, tx, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createWritesThePaymentAndAnOutboxRowKeyedByPaymentId() {
        PaymentService.CreateResult result = service.create(null, "ACC-1", "ACC-2", AMOUNT, "INR");

        Payment payment = result.payment();
        assertThat(result.replayed()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.INITIATED);

        ArgumentCaptor<OutboxEvent> row = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(row.capture());
        assertThat(row.getValue().getTopic()).isEqualTo(Topics.INITIATED);
        assertThat(row.getValue().getAggregateId()).isEqualTo(payment.getId().toString());
        assertThat(row.getValue().getPayload()).contains(payment.getId().toString()).contains("ACC-2");
        assertThat(row.getValue().getSentAt()).isNull();
        verify(idempotency, never()).saveAndFlush(any());
    }

    @Test
    void aNewIdempotencyKeyIsRecordedWithThePayment() {
        when(idempotency.findById("key-1")).thenReturn(Optional.empty());

        PaymentService.CreateResult result = service.create("key-1", "ACC-1", "ACC-2", AMOUNT, "INR");

        ArgumentCaptor<IdempotencyRecord> record = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotency).saveAndFlush(record.capture());
        assertThat(record.getValue().getKey()).isEqualTo("key-1");
        assertThat(record.getValue().getPaymentId()).isEqualTo(result.payment().getId());
    }

    @Test
    void aRepeatedKeyWithTheSameBodyReturnsTheOriginalPaymentAndWritesNothing() {
        Payment original = Payment.initiate("ACC-1", "ACC-2", AMOUNT, "INR");
        String hash = PaymentService.requestHash("ACC-1", "ACC-2", AMOUNT, "INR");
        when(idempotency.findById("key-1")).thenReturn(Optional.of(new IdempotencyRecord("key-1", original.getId(), hash)));
        when(payments.findById(original.getId())).thenReturn(Optional.of(original));

        PaymentService.CreateResult result = service.create("key-1", "ACC-1", "ACC-2", AMOUNT, "INR");

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment()).isSameAs(original);
        verify(payments, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void aRepeatedKeyWithADifferentBodyIsRejected() {
        Payment original = Payment.initiate("ACC-1", "ACC-2", AMOUNT, "INR");
        String hash = PaymentService.requestHash("ACC-1", "ACC-2", AMOUNT, "INR");
        when(idempotency.findById("key-1")).thenReturn(Optional.of(new IdempotencyRecord("key-1", original.getId(), hash)));

        assertThatThrownBy(() -> service.create("key-1", "ACC-1", "ACC-2", new BigDecimal("999.00"), "INR"))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(outbox, never()).save(any());
    }

    @Test
    void whenTwoRequestsRaceOnOneKeyTheLoserReturnsTheWinnersPayment() {
        Payment winner = Payment.initiate("ACC-1", "ACC-2", AMOUNT, "INR");
        String hash = PaymentService.requestHash("ACC-1", "ACC-2", AMOUNT, "INR");
        // First lookup: nothing yet. The insert then hits the primary key. Second lookup: the winner's record.
        when(idempotency.findById("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IdempotencyRecord("key-1", winner.getId(), hash)));
        when(idempotency.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(payments.findById(winner.getId())).thenReturn(Optional.of(winner));

        PaymentService.CreateResult result = service.create("key-1", "ACC-1", "ACC-2", AMOUNT, "INR");

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment()).isSameAs(winner);
    }

    @Test
    void theRequestHashIgnoresTrailingZerosInTheAmount() {
        assertThat(PaymentService.requestHash("A", "B", new BigDecimal("250"), "INR"))
                .isEqualTo(PaymentService.requestHash("A", "B", new BigDecimal("250.00"), "INR"));
    }

    @Test
    void aFlaggedPaymentCanBePutOnHoldAndHoldingItAgainChangesNothing() {
        Payment payment = Payment.initiate("ACC-1", "ACC-2", AMOUNT, "INR");
        payment.applyScore(70, "FLAGGED", "High amount");

        payment.hold("Reviewer approved");
        java.time.Instant firstHeldAt = payment.getHeldAt();
        payment.hold("Second attempt");

        assertThat(payment.getStatus()).isEqualTo(Payment.Status.ON_HOLD);
        assertThat(payment.getHoldReason()).isEqualTo("Reviewer approved");
        assertThat(payment.getHeldAt()).isEqualTo(firstHeldAt);
    }

    @Test
    void aPaymentThatIsNotFlaggedCannotBePutOnHold() {
        Payment approved = Payment.initiate("ACC-1", "ACC-2", AMOUNT, "INR");
        approved.applyScore(0, "APPROVED", "No rule triggered");

        assertThatThrownBy(() -> approved.hold("why not")).isInstanceOf(PaymentStateException.class);
        assertThat(approved.getStatus()).isEqualTo(Payment.Status.APPROVED);
    }

    @Test
    void aReplayedScoreEventDoesNotReleaseAHeldPayment() {
        Payment payment = Payment.initiate("ACC-1", "ACC-2", AMOUNT, "INR");
        payment.applyScore(70, "FLAGGED", "High amount");
        payment.hold("Reviewer approved");

        payment.applyScore(70, "FLAGGED", "High amount");

        assertThat(payment.getStatus()).isEqualTo(Payment.Status.ON_HOLD);
    }

    @Test
    void holdingThroughTheServiceReturnsEmptyForAnUnknownPayment() {
        java.util.UUID unknown = java.util.UUID.randomUUID();
        when(payments.findById(unknown)).thenReturn(Optional.empty());

        assertThat(service.hold(unknown, "reason")).isEmpty();
    }

    @Test
    void applyingAScoreTwiceIsIdempotent() {
        Payment payment = Payment.initiate("ACC-1", "ACC-2", new BigDecimal("10.00"), "INR");

        payment.applyScore(60, "FLAGGED", "High amount");
        payment.applyScore(60, "FLAGGED", "High amount");

        assertThat(payment.getStatus()).isEqualTo(Payment.Status.FLAGGED);
        assertThat(payment.getRiskScore()).isEqualTo(60);
    }
}
