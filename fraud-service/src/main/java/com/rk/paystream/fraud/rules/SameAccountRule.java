package com.rk.paystream.fraud.rules;

import com.rk.paystream.fraud.Events.PaymentInitiated;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SameAccountRule implements FraudRule {

    @Override
    public Optional<Hit> evaluate(PaymentInitiated event) {
        return event.debtorAccount().equalsIgnoreCase(event.creditorAccount())
                ? Optional.of(new Hit("SAME_ACCOUNT", 20, "Same debtor and creditor"))
                : Optional.empty();
    }
}
