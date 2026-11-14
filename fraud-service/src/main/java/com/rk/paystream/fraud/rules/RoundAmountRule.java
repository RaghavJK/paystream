package com.rk.paystream.fraud.rules;

import com.rk.paystream.fraud.Events.PaymentInitiated;
import com.rk.paystream.fraud.FraudProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RoundAmountRule implements FraudRule {

    private final FraudProperties properties;

    public RoundAmountRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Hit> evaluate(PaymentInitiated event) {
        return event.amount().remainder(properties.roundUnit()).signum() == 0
                ? Optional.of(new Hit("ROUND_AMOUNT", 10, "Round amount"))
                : Optional.empty();
    }
}
