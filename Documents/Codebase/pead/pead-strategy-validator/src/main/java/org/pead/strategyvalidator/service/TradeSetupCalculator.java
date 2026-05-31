package org.pead.strategyvalidator.service;

import lombok.RequiredArgsConstructor;
import org.pead.common.config.StrategyConfig;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates the trade setup for a validated PEAD signal.
 *
 * Entry  = earningsCandleHigh  (Day+2 breakout above this level triggers entry)
 * Stop   = earningsCandleLow   (invalidates the setup)
 * Target1 = entry + (entry - stop) * profitTarget1R   (default 2R)
 * Target2 = entry + (entry - stop) * profitTarget2R   (default 3R)
 */
@Service
@RequiredArgsConstructor
public class TradeSetupCalculator {

    private final StrategyConfig strategyConfig;

    public record TradeSetup(
            BigDecimal entryPrice,
            BigDecimal stopLoss,
            BigDecimal target1,
            BigDecimal target2,
            BigDecimal riskRewardRatio
    ) {}

    public TradeSetup calculate(double earningsCandleHigh, double earningsCandleLow) {
        BigDecimal entry = BigDecimal.valueOf(earningsCandleHigh).setScale(4, RoundingMode.HALF_UP);
        BigDecimal stop  = BigDecimal.valueOf(earningsCandleLow).setScale(4, RoundingMode.HALF_UP);
        BigDecimal risk  = entry.subtract(stop);

        BigDecimal target1 = entry.add(
                risk.multiply(BigDecimal.valueOf(strategyConfig.profitTarget1R()))
        ).setScale(4, RoundingMode.HALF_UP);

        BigDecimal target2 = entry.add(
                risk.multiply(BigDecimal.valueOf(strategyConfig.profitTarget2R()))
        ).setScale(4, RoundingMode.HALF_UP);

        BigDecimal rrRatio = BigDecimal.valueOf(strategyConfig.profitTarget1R());

        return new TradeSetup(entry, stop, target1, target2, rrRatio);
    }
}
