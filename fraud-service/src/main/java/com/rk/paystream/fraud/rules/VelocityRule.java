package com.rk.paystream.fraud.rules;

import com.rk.paystream.fraud.Events.PaymentInitiated;
import com.rk.paystream.fraud.FraudProperties;
import com.rk.paystream.fraud.RiskAssessmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Flags a debtor who sends many payments in a short window, by counting that debtor's earlier
 * assessments in MongoDB.
 *
 * Deterministic on replay: the window is measured back from the event's own occurredAt, and the
 * payment being scored is excluded from the count.
 *
 * Known limit: the count is approximate under concurrency. Events are keyed by payment ID, so two
 * payments from one debtor can be on different partitions and be scored at the same moment, each
 * missing the other. TODO (roadmap step 6): replace with a Kafka Streams windowed count on a
 * stream re-keyed by debtor, which is exact, and compare the two.
 */
@Component
public class VelocityRule implements FraudRule {

    private final RiskAssessmentRepository assessments;
    private final FraudProperties properties;

    public VelocityRule(RiskAssessmentRepository assessments, FraudProperties properties) {
        this.assessments = assessments;
        this.properties = properties;
    }

    @Override
    public Optional<Hit> evaluate(PaymentInitiated event) {
        Instant to = event.occurredAt();
        Instant from = to.minusSeconds(properties.velocityWindowSeconds());
        long earlier = assessments.countRecentByDebtor(event.debtorAccount(), from, to, event.paymentId().toString());
        if (earlier < properties.velocityMaxPayments()) {
            return Optional.empty();
        }
        return Optional.of(new Hit("HIGH_VELOCITY", 50, "High velocity: " + (earlier + 1) + " payments in "
                + properties.velocityWindowSeconds() + " s"));
    }
}
