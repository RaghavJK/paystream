package com.rk.paystream.fraud.rules;

import com.rk.paystream.fraud.Events.PaymentInitiated;

import java.util.Optional;

/**
 * Strategy pattern: one class per rule. To add a rule, add a @Component that implements this
 * interface; FraudRules picks it up through constructor injection and nothing else changes.
 */
public interface FraudRule {

    /** A rule that fired: its name, the points it adds and a human-readable reason. */
    record Hit(String rule, int points, String reason) {
    }

    Optional<Hit> evaluate(PaymentInitiated event);
}
