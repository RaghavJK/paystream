package com.rk.paystream.fraud;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface RiskAssessmentRepository extends MongoRepository<RiskAssessment, String> {

    /** How many other payments this debtor made in [from, to]. Uses the debtor_time index. */
    @Query(value = "{ 'debtorAccount': ?0, 'occurredAt': { $gte: ?1, $lte: ?2 }, '_id': { $ne: ?3 } }", count = true)
    long countRecentByDebtor(String debtorAccount, Instant from, Instant to, String excludePaymentId);

    List<RiskAssessment> findTop50ByDecisionOrderByScoredAtDesc(String decision);
}
