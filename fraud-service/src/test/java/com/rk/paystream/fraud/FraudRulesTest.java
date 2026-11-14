package com.rk.paystream.fraud;

import com.rk.paystream.fraud.Events.PaymentInitiated;
import com.rk.paystream.fraud.rules.FraudRule;
import com.rk.paystream.fraud.rules.HighAmountRule;
import com.rk.paystream.fraud.rules.RoundAmountRule;
import com.rk.paystream.fraud.rules.SameAccountRule;
import com.rk.paystream.fraud.rules.VelocityRule;
import com.rk.paystream.fraud.rules.WatchlistRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FraudRulesTest {

    private static final FraudProperties PROPS = new FraudProperties(
            50, new BigDecimal("100000"), new BigDecimal("10000"), List.of("WATCH"), 60, 4);

    private RiskAssessmentRepository repository;
    private FraudRules rules;

    @BeforeEach
    void setUp() {
        repository = mock(RiskAssessmentRepository.class); // counts default to 0: no velocity
        List<FraudRule> all = List.of(new HighAmountRule(PROPS), new WatchlistRule(PROPS), new RoundAmountRule(PROPS),
                new SameAccountRule(), new VelocityRule(repository, PROPS));
        rules = new FraudRules(all, PROPS);
    }

    private static PaymentInitiated payment(String debtor, String creditor, String amount) {
        return new PaymentInitiated(UUID.randomUUID(), debtor, creditor, new BigDecimal(amount), "INR", Instant.now());
    }

    @Test
    void anOrdinaryPaymentIsApprovedWithNoHits() {
        FraudRules.Result result = rules.score(payment("ACC-1", "ACC-2", "1250.50"));

        assertThat(result.decision()).isEqualTo(FraudRules.APPROVED);
        assertThat(result.score()).isZero();
        assertThat(result.hits()).isEmpty();
        assertThat(result.reason()).isEqualTo("No rule triggered");
    }

    @Test
    void aHighRoundAmountIsFlaggedAndListsBothRulesStrongestFirst() {
        FraudRules.Result result = rules.score(payment("ACC-1", "ACC-2", "250000.00"));

        assertThat(result.decision()).isEqualTo(FraudRules.FLAGGED);
        assertThat(result.score()).isEqualTo(70);
        assertThat(result.hits()).extracting(FraudRule.Hit::rule).containsExactly("HIGH_AMOUNT", "ROUND_AMOUNT");
        assertThat(result.reason()).isEqualTo("High amount; Round amount");
    }

    @Test
    void aWatchlistedCreditorIsFlaggedExactlyAtTheThreshold() {
        FraudRules.Result result = rules.score(payment("ACC-1", "watch-77", "99.00"));

        assertThat(result.decision()).isEqualTo(FraudRules.FLAGGED);
        assertThat(result.score()).isEqualTo(50);
    }

    @Test
    void payingYourselfAloneIsNotEnoughToFlag() {
        FraudRules.Result result = rules.score(payment("ACC-1", "acc-1", "99.00"));

        assertThat(result.decision()).isEqualTo(FraudRules.APPROVED);
        assertThat(result.score()).isEqualTo(20);
    }

    @Test
    void theScoreIsCappedAtOneHundred() {
        FraudRules.Result result = rules.score(payment("ACC-1", "WATCH-1", "500000"));

        assertThat(result.score()).isEqualTo(100); // 60 + 50 + 10 = 120, capped
    }

    @Test
    void theFifthPaymentFromOneDebtorInsideTheWindowIsFlagged() {
        when(repository.countRecentByDebtor(eq("ACC-9"), any(Instant.class), any(Instant.class), anyString()))
                .thenReturn(4L);

        FraudRules.Result result = rules.score(payment("ACC-9", "ACC-2", "120.00"));

        assertThat(result.decision()).isEqualTo(FraudRules.FLAGGED);
        assertThat(result.reason()).isEqualTo("High velocity: 5 payments in 60 s");
    }

    @Test
    void fourPaymentsInsideTheWindowAreStillAllowed() {
        when(repository.countRecentByDebtor(eq("ACC-9"), any(Instant.class), any(Instant.class), anyString()))
                .thenReturn(3L);

        assertThat(rules.score(payment("ACC-9", "ACC-2", "120.00")).decision()).isEqualTo(FraudRules.APPROVED);
    }
}
