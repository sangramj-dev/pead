package org.pead.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Financial math utilities for trading calculations.
 * All methods use double for performance in indicator calculations.
 * Use BigDecimal variants for monetary amounts.
 */
public final class FinancialMath {

    private static final int SCALE = 4;

    private FinancialMath() {}

    /**
     * Calculate surprise percentage: (actual - estimate) / |estimate| * 100
     * Returns null if estimate is zero or null.
     */
    public static Double surprisePct(Double actual, Double estimate) {
        if (actual == null || estimate == null || estimate == 0.0) {
            return null;
        }
        return ((actual - estimate) / Math.abs(estimate)) * 100.0;
    }

    /**
     * Calculate gap percentage: (open - prevClose) / prevClose * 100
     */
    public static double gapPct(double openPrice, double prevClose) {
        if (prevClose == 0.0) return 0.0;
        return ((openPrice - prevClose) / prevClose) * 100.0;
    }

    /**
     * Calculate EMA: EMA_t = price * k + EMA_{t-1} * (1 - k)
     * where k = 2 / (period + 1)
     */
    public static double ema(double price, double prevEma, int period) {
        double k = 2.0 / (period + 1);
        return price * k + prevEma * (1 - k);
    }

    /**
     * Calculate EMA multiplier k = 2 / (period + 1)
     */
    public static double emaMultiplier(int period) {
        return 2.0 / (period + 1);
    }

    /**
     * Calculate position size in shares using 1% risk rule.
     * shares = floor(accountSize * riskPct / |entry - stop|)
     */
    public static int positionSizeShares(double accountSize, double riskPct,
                                          double entryPrice, double stopLoss) {
        double riskAmount = accountSize * riskPct;
        double riskPerShare = Math.abs(entryPrice - stopLoss);
        if (riskPerShare == 0.0) return 0;
        return (int) Math.floor(riskAmount / riskPerShare);
    }

    /**
     * Calculate profit target price given risk-reward ratio.
     * For LONG: target = entry + (entry - stop) * rrRatio
     * For SHORT: target = entry - (stop - entry) * rrRatio
     */
    public static double profitTarget(double entryPrice, double stopLoss, double rrRatio) {
        double riskPerShare = entryPrice - stopLoss;  // positive for long, negative for short
        return entryPrice + (riskPerShare * rrRatio);
    }

    /**
     * Calculate relative volume: todayVolume / avgVolume
     */
    public static double relativeVolume(long todayVolume, long avgVolume) {
        if (avgVolume == 0L) return 0.0;
        return (double) todayVolume / avgVolume;
    }

    /**
     * Round to standard financial precision (4 decimal places)
     */
    public static double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
