package com.rk.paystream.fraud.rules;

import com.rk.paystream.fraud.Events.PaymentInitiated;
import com.rk.paystream.fraud.FraudProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/** Stand-in for a sanctions or watchlist lookup: matches the creditor account by prefix. */
@Component
public class WatchlistRule implements FraudRule {

    private final FraudProperties properties;

    public WatchlistRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Hit> evaluate(PaymentInitiated event) {
        String creditor = event.creditorAccount().toUpperCase(Locale.ROOT);
        boolean listed = properties.watchlistPrefixes().stream()
                .anyMatch(prefix -> creditor.startsWith(prefix.toUpperCase(Locale.ROOT)));
        return listed ? Optional.of(new Hit("WATCHLIST", 50, "Creditor on watchlist")) : Optional.empty();
    }
}
