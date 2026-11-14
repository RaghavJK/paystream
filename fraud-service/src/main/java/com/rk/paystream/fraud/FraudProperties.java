package com.rk.paystream.fraud;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rule thresholds, bound from "paystream.fraud.*" in application.yml.
 * Changing a threshold is a configuration change, not a code change.
 */
@ConfigurationProperties(prefix = "paystream.fraud")
public record FraudProperties(
        @DefaultValue("50") int flagThreshold,
        @DefaultValue("100000") BigDecimal highAmount,
        @DefaultValue("10000") BigDecimal roundUnit,
        @DefaultValue("WATCH") List<String> watchlistPrefixes,
        @DefaultValue("60") int velocityWindowSeconds,
        @DefaultValue("4") int velocityMaxPayments) {
}
