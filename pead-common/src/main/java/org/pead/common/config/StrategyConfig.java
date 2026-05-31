package org.pead.common.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Strategy configuration loaded from application.yml / K8s ConfigMap.
 * All parameters are configurable without code changes.
 * Hot-reload supported via @RefreshScope on consuming beans.
 */
@ConfigurationProperties(prefix = "pead.strategy")
@Validated
public record StrategyConfig(

        // =====================================================================
        // SCANNER PRE-FILTER THRESHOLDS
        // =====================================================================

        @DecimalMin("0.0") @DecimalMax("50.0")
        double minEpsSurprisePct,           // default 3.0 — minimum EPS beat %

        @DecimalMin("0.0") @DecimalMax("50.0")
        double minRevenueSurprisePct,       // default 2.0 — minimum revenue beat %

        @DecimalMin("2.0") @DecimalMax("30.0")
        double minGapPct,                   // default 5.0 — minimum gap % (abs)

        @DecimalMin("1.0") @DecimalMax("20.0")
        double minRelativeVolume,           // default 2.0 — minimum relative volume

        // =====================================================================
        // SCORING THRESHOLDS
        // =====================================================================

        @Min(0) @Max(100)
        int minPeadScore,                   // default 60 — minimum PEAD score to publish signal

        // =====================================================================
        // RISK MANAGEMENT
        // =====================================================================

        @DecimalMin("0.001") @DecimalMax("0.05")
        double maxRiskPctPerTrade,          // default 0.01 — max 1% account risk per trade

        @Min(1) @Max(20)
        int maxConcurrentPositions,         // default 5 — max open positions

        @DecimalMin("0.01") @DecimalMax("0.10")
        double dailyLossLimitPct,           // default 0.02 — halt if daily P&L < -2%

        @DecimalMin("0.05") @DecimalMax("0.50")
        double maxDrawdownPct,              // default 0.10 — halt if drawdown > 10%

        @DecimalMin("0.01") @DecimalMax("0.20")
        double maxPositionSizePct,          // default 0.10 — no position > 10% of portfolio

        @DecimalMin("0.05") @DecimalMax("0.30")
        double maxSectorConcentrationPct,   // default 0.20 — max 20% in any one sector

        // =====================================================================
        // PROFIT TARGETS
        // =====================================================================

        @DecimalMin("1.0") @DecimalMax("10.0")
        double profitTarget1R,              // default 2.0 — first target at 2R

        @DecimalMin("1.5") @DecimalMax("15.0")
        double profitTarget2R,              // default 3.0 — second target at 3R

        // =====================================================================
        // STRATEGY SWITCHES
        // =====================================================================

        boolean enableShortSelling,         // default false — Phase 1 long-only

        List<String> excludedSectors        // sectors to exclude from trading

) {
    // Compact constructor with defaults
    public StrategyConfig {
        if (excludedSectors == null) {
            excludedSectors = List.of();
        }
    }
}
