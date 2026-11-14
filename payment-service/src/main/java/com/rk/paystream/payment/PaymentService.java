package com.rk.paystream.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rk.paystream.payment.Events.PaymentInitiated;
import com.rk.paystream.payment.Events.PaymentScored;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    /** replayed = true means the request was a repeat and no new payment was created. */
    public record CreateResult(Payment payment, boolean replayed) {
    }

    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final IdempotencyRepository idempotency;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public PaymentService(PaymentRepository payments, OutboxRepository outbox, IdempotencyRepository idempotency,
                          TransactionTemplate tx, ObjectMapper mapper) {
        this.payments = payments;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.tx = tx;
        this.mapper = mapper;
    }

    /**
     * Creates a payment. The payment row, the outbox row and (when a key is given) the idempotency
     * row are written in ONE database transaction. Kafka is not touched here: OutboxRelay publishes
     * the outbox row afterwards. So an event exists if, and only if, the payment was committed.
     *
     * @param idempotencyKey optional client-generated key; a repeat with the same key and body
     *                       returns the original payment instead of creating a second one
     */
    public CreateResult create(String idempotencyKey, String debtorAccount, String creditorAccount,
                               BigDecimal amount, String currency) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
        String hash = requestHash(debtorAccount, creditorAccount, amount, currency);

        if (key != null) {
            Optional<Payment> earlier = findReplay(key, hash);
            if (earlier.isPresent()) {
                return new CreateResult(earlier.get(), true);
            }
        }

        try {
            Payment created = tx.execute(status -> {
                Payment payment = payments.save(Payment.initiate(debtorAccount, creditorAccount, amount, currency));
                PaymentInitiated event = new PaymentInitiated(payment.getId(), debtorAccount, creditorAccount,
                        amount, currency, Instant.now());
                // Aggregate ID becomes the Kafka key, so all events for one payment share a partition.
                outbox.save(OutboxEvent.pending(Topics.INITIATED, payment.getId().toString(), toJson(event)));
                if (key != null) {
                    // Flush now so a concurrent duplicate fails here, on the primary key, and rolls everything back.
                    idempotency.saveAndFlush(new IdempotencyRecord(key, payment.getId(), hash));
                }
                return payment;
            });
            return new CreateResult(created, false);
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same key raced; the other one won. Return its payment.
            if (key == null) {
                throw e;
            }
            return findReplay(key, hash).map(p -> new CreateResult(p, true)).orElseThrow(() -> e);
        }
    }

    /** Idempotent: applying the same score twice leaves the payment unchanged. */
    public void applyScore(PaymentScored event) {
        tx.executeWithoutResult(status -> payments.findById(event.paymentId())
                .ifPresent(p -> p.applyScore(event.score(), event.decision(), event.reason())));
    }

    /**
     * Puts a flagged payment on hold. Empty if the payment does not exist.
     *
     * @throws PaymentStateException if the payment is not FLAGGED (or already ON_HOLD)
     */
    public Optional<Payment> hold(UUID id, String reason) {
        return tx.execute(status -> {
            Optional<Payment> payment = payments.findById(id);
            payment.ifPresent(p -> p.hold(reason));
            return payment;
        });
    }

    public List<Payment> recent() {
        return payments.findTop50ByOrderByCreatedAtDesc();
    }

    public Optional<Payment> find(UUID id) {
        return payments.findById(id);
    }

    private Optional<Payment> findReplay(String key, String hash) {
        return idempotency.findById(key).map(record -> {
            if (!record.getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException(key);
            }
            return payments.findById(record.getPaymentId())
                    .orElseThrow(() -> new IllegalStateException("Idempotency record without payment: " + key));
        });
    }

    static String requestHash(String debtorAccount, String creditorAccount, BigDecimal amount, String currency) {
        String canonical = String.join("|", debtorAccount, creditorAccount,
                amount.stripTrailingZeros().toPlainString(), currency);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String toJson(Object event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event", e);
        }
    }
}
