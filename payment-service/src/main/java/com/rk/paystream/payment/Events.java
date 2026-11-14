package com.rk.paystream.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event contracts. Each service keeps its own copy on purpose: services share a
 * contract, not a library. TODO (roadmap step 5): move these to Avro with a schema registry.
 */
public final class Events {

    public record PaymentInitiated(UUID paymentId, String debtorAccount, String creditorAccount,
                                   BigDecimal amount, String currency, Instant occurredAt) {
    }

    public record PaymentScored(UUID paymentId, int score, String decision, String reason, Instant scoredAt) {
    }

    private Events() {
    }
}
