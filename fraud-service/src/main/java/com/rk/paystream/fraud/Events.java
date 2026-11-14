package com.rk.paystream.fraud;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** This service's own copy of the event contracts. See payment-service Events for the reason. */
public final class Events {

    public static final String TOPIC_INITIATED = "payments.initiated";
    public static final String TOPIC_INITIATED_DLT = TOPIC_INITIATED + ".DLT";
    public static final String TOPIC_SCORED = "payments.scored";

    public record PaymentInitiated(UUID paymentId, String debtorAccount, String creditorAccount,
                                   BigDecimal amount, String currency, Instant occurredAt) {
    }

    public record PaymentScored(UUID paymentId, int score, String decision, String reason, Instant scoredAt) {
    }

    private Events() {
    }
}
