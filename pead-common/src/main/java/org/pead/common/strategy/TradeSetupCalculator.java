package org.pead.common.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates trade setup levels (entry, stop, targets) from earnings candle data.
 * Pure Java — no Spring dependencies; instantiable with {@code new}.
 *
 * <p>For a LONG setup:
 * <ul>
 *   <li>Entry  = earnings candle high (breakout level)</li>
 *   <li>Stop   = earnings candle low</li>
 *   <li>Target1 = entry + risk * target1R</li>
 *   <li>Target2 = entry + risk * target2R</li>
 * </ul>
 */
public final class TradeSetupCalculator {

    private static final int PRICE_SCALE = 2;

    private final double target1R;
    private final double target2R;

    /**
     * @param target1R first profit target expressed as a multiple of risk (e.g. 2.0 = 2R)
     * @param target2R second profit target expressed as a multiple of risk (e.g. 3.0 = 3R)
     */
    public TradeSetupCalculator(double target1R, double target2R) {
        if (target1R <= 0 || target2R <= 0) {
            throw new IllegalArgumentException("Risk-reward ratios must be positive");
        }
        if (target2R <= target1R) {
            throw new IllegalArgumentException("target2R must be greater than target1R");
        }
        this.target1R = target1R;
        this.target2R = target2R;
    }

    /**
     * Calculate trade setup levels from the earnings candle range.
     *
     * @param earningsCandleHigh high price of the earnings candle
     * @param earningsCandleLow  low price of the earnings candle
     * @return {@link TradeSetup} with computed levels
     * @throws IllegalArgumentException if high <= low or prices are non-positive
     */
    public TradeSetup calculate(double earningsCandleHigh, double earningsCandleLow) {
        if (earningsCandleHigh <= 0 || earningsCandleLow <= 0) {
            throw new IllegalArgumentException("Candle prices must be positive");
        }
        if (earningsCandleHigh <= earningsCandleLow) {
            throw new IllegalArgumentException("Candle high must be greater than candle low");
        }

        BigDecimal entry    = bd(earningsCandleHigh);
        BigDecimal stopLoss = bd(earningsCandleLow);
        BigDecimal risk     = entry.subtract(stopLoss);

        BigDecimal target1  = entry.add(risk.multiply(BigDecimal.valueOf(target1R)))
                                   .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal target2  = entry.add(risk.multiply(BigDecimal.valueOf(target2R)))
                                   .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        // Risk-reward ratio relative to target1 (reward / risk)
        double riskRewardRatio = target1R;

        return new TradeSetup(entry, stopLoss, target1, target2, riskRewardRatio);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Immutable trade setup: entry, stop-loss, and two profit targets.
     *
     * @param entryPrice      entry level (earnings candle high)
     * @param stopLoss        initial stop-loss (earnings candle low)
     * @param target1         first profit target
     * @param target2         second profit target
     * @param riskRewardRatio risk-reward ratio to first target
     */
    public record TradeSetup(
            BigDecimal entryPrice,
            BigDecimal stopLoss,
            BigDecimal target1,
            BigDecimal target2,
            double riskRewardRatio
    ) {}
}
