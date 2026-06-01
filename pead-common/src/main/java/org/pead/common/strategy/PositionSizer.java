package org.pead.common.strategy;

/**
 * Calculates position sizes based on account equity, risk tolerance, and portfolio limits.
 * Pure Java — no Spring dependencies; instantiable with {@code new}.
 *
 * <p>Sizing logic:
 * <ol>
 *   <li>Risk-based shares: floor(accountEquity * riskPctPerTrade / |entry - stop|)</li>
 *   <li>Cap to max position size: floor(accountEquity * maxPositionSizePct / entry)</li>
 *   <li>Return the lesser of the two; return 0 if entry == stop.</li>
 * </ol>
 */
public final class PositionSizer {

    private final double riskPctPerTrade;
    private final int    maxConcurrentPositions;
    private final double maxPositionSizePct;

    /**
     * @param riskPctPerTrade        fraction of account equity to risk per trade (e.g. 0.01 = 1%)
     * @param maxConcurrentPositions maximum number of simultaneously open positions
     * @param maxPositionSizePct     maximum position value as fraction of account equity (e.g. 0.10 = 10%)
     */
    public PositionSizer(double riskPctPerTrade, int maxConcurrentPositions, double maxPositionSizePct) {
        if (riskPctPerTrade <= 0 || riskPctPerTrade > 1.0) {
            throw new IllegalArgumentException("riskPctPerTrade must be between 0 and 1");
        }
        if (maxConcurrentPositions <= 0) {
            throw new IllegalArgumentException("maxConcurrentPositions must be positive");
        }
        if (maxPositionSizePct <= 0 || maxPositionSizePct > 1.0) {
            throw new IllegalArgumentException("maxPositionSizePct must be between 0 and 1");
        }
        this.riskPctPerTrade        = riskPctPerTrade;
        this.maxConcurrentPositions = maxConcurrentPositions;
        this.maxPositionSizePct     = maxPositionSizePct;
    }

    /**
     * Calculate the number of shares to buy given account equity and trade levels.
     *
     * @param accountEquity total account equity in currency units
     * @param entryPrice    planned entry price per share
     * @param stopLoss      planned stop-loss price per share
     * @return number of shares (0 if entry equals stop or inputs are non-positive)
     */
    public int calculateShares(double accountEquity, double entryPrice, double stopLoss) {
        if (accountEquity <= 0 || entryPrice <= 0) return 0;

        double riskPerShare = Math.abs(entryPrice - stopLoss);
        if (riskPerShare == 0.0) return 0;

        // Risk-based calculation
        double riskAmount      = accountEquity * riskPctPerTrade;
        int    riskBasedShares = (int) Math.floor(riskAmount / riskPerShare);

        // Maximum position size cap
        double maxPositionValue = accountEquity * maxPositionSizePct;
        int    cappedShares     = (int) Math.floor(maxPositionValue / entryPrice);

        return Math.min(riskBasedShares, cappedShares);
    }

    /**
     * Check whether a new position can be opened given the current open position count.
     *
     * @param currentOpenPositions number of currently open positions
     * @return true if a new position can be opened
     */
    public boolean canOpenPosition(int currentOpenPositions) {
        return currentOpenPositions < maxConcurrentPositions;
    }
}
