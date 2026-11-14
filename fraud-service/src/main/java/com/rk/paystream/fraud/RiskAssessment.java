package com.rk.paystream.fraud;

import com.rk.paystream.fraud.rules.FraudRule.Hit;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One document per payment. The payment ID is the document ID, so re-processing the same event
 * overwrites instead of duplicating. The rule hits are embedded: they are always read with the
 * assessment and never on their own, which is the case a document store handles well.
 *
 * The compound index serves VelocityRule's "this debtor, this time window" count.
 */
@Document("risk_assessments")
@CompoundIndex(name = "debtor_time", def = "{'debtorAccount': 1, 'occurredAt': -1}")
public record RiskAssessment(@Id String paymentId,
                             String debtorAccount,
                             Instant occurredAt,
                             int score,
                             String decision,
                             String reason,
                             List<Hit> hits,
                             Instant scoredAt) {
}
