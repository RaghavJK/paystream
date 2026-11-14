package com.rk.paystream.fraud;

import com.rk.paystream.fraud.Events.PaymentInitiated;
import com.rk.paystream.fraud.rules.FraudRule;
import com.rk.paystream.fraud.rules.FraudRule.Hit;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The rule engine: runs every FraudRule, adds the points of those that fire, caps the total at
 * 100 and decides. Spring injects every FraudRule bean, so this class never changes when a rule
 * is added (open/closed principle).
 */
@Service
public class FraudRules {

    public static final String FLAGGED = "FLAGGED";
    public static final String APPROVED = "APPROVED";
    private static final int MAX_SCORE = 100;

    public record Result(int score, String decision, String reason, List<Hit> hits) {
    }

    private final List<FraudRule> rules;
    private final FraudProperties properties;

    public FraudRules(List<FraudRule> rules, FraudProperties properties) {
        this.rules = List.copyOf(rules);
        this.properties = properties;
    }

    public Result score(PaymentInitiated event) {
        List<Hit> hits = rules.stream()
                .map(rule -> rule.evaluate(event))
                .flatMap(Optional::stream)
                // Strongest signal first, so the reason text is stable whatever order Spring injects the rules in.
                .sorted(Comparator.<Hit>comparingInt(Hit::points).reversed().thenComparing(Hit::rule))
                .toList();

        int score = Math.min(hits.stream().mapToInt(Hit::points).sum(), MAX_SCORE);
        String decision = score >= properties.flagThreshold() ? FLAGGED : APPROVED;
        String reason = hits.isEmpty()
                ? "No rule triggered"
                : hits.stream().map(Hit::reason).collect(Collectors.joining("; "));
        return new Result(score, decision, reason, hits);
    }
}
