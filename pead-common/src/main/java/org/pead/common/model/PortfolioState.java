package org.pead.common.model;

import org.pead.common.domain.TradeDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable container representing the live state of a portfolio during backtesting
 * or live trading simulation.
 *
 * <p>Not thread-safe — use within a single-threaded backtest engine.
 */
public final class PortfolioState {

    private BigDecimal          cash;
    private BigDecimal          peakEquity;
    private final List<OpenPosition> openPositions;
    private final List<TradeRecord>  closedTrades;

    /**
     * @param initialCapital starting cash balance
     */
    public PortfolioState(BigDecimal initialCapital) {
        if (initialCapital == null || initialCapital.signum() <= 0) {
            throw new IllegalArgumentException("Initial capital must be positive");
        }
        this.cash          = initialCapital;
        this.peakEquity    = initialCapital;
        this.openPositions = new ArrayList<>();
        this.closedTrades  = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public BigDecimal getCash() {
        return cash;
    }

    public void setCash(BigDecimal cash) {
        this.cash = cash;
    }

    public BigDecimal getPeakEquity() {
        return peakEquity;
    }

    public void setPeakEquity(BigDecimal peakEquity) {
        this.peakEquity = peakEquity;
    }

    public List<OpenPosition> getOpenPositions() {
        return openPositions;
    }

    public List<TradeRecord> getClosedTrades() {
        return closedTrades;
    }

    // -------------------------------------------------------------------------
    // Inner record: an open (unrealised) position
    // -------------------------------------------------------------------------

    /**
     * Represents an active position that has not yet been closed.
     *
     * @param ticker       trading symbol
     * @param direction    LONG or SHORT
     * @param quantity     number of shares held
     * @param entryPrice   price at which the position was entered
     * @param stopLoss     initial stop-loss price
     * @param target1      first profit target price
     * @param target2      second profit target price
     * @param entryDate    date the position was opened
     * @param peadScore    PEAD composite score at signal time
     * @param currentPrice latest market price (updated during backtest simulation)
     * @param target1Hit   true once the first profit target has been reached
     */
    public record OpenPosition(
            String         ticker,
            TradeDirection direction,
            int            quantity,
            BigDecimal     entryPrice,
            BigDecimal     stopLoss,
            BigDecimal     target1,
            BigDecimal     target2,
            LocalDate      entryDate,
            int            peadScore,
            BigDecimal     currentPrice,
            boolean        target1Hit
    ) {}
}
