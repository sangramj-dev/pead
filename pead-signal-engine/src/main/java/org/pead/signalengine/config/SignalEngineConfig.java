package org.pead.signalengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pead.signal-engine")
public record SignalEngineConfig(
        double defaultAccountEquity,
        int signalExpiryDays
) {
    public SignalEngineConfig {
        if (defaultAccountEquity <= 0) {
            defaultAccountEquity = 1_000_000;
        }
        if (signalExpiryDays <= 0) {
            signalExpiryDays = 5;
        }
    }
}
