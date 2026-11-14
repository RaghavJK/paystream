package com.rk.paystream.fraud.rules;

import com.rk.paystream.fraud.Events.PaymentInitiated;
import com.rk.paystream.fraud.FraudProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HighAmountRule implements FraudRule {

    private final FraudProperties properties;

    public HighAmountRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Hit> evaluate(PaymentInitiated event) {
        return event.amount().compareTo(properties.highAmount()) >= 0
                ? Optional.of(new Hit("HIGH_AMOUNT", 60, "High amount"))
                : Optional.empty();
    }
}
