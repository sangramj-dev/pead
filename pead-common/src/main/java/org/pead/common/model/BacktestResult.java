package org.pead.common.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Immutable summary of a completed backtest run.
 *
 * @param totalTrades      total number of completed trades
 * @param winningTrades    number of trades with positive P&L
 * @param losingTrades     number of trades with negative P&L
 * @param winRate          winning trades / total trades (0.0–1.0)
 * @param profitFactor     gross profit / gross loss (higher is better; 0 if no losses)
 * @param sharpeRatio      annualised Sharpe ratio of daily returns
 * @param maxDrawdownPct   maximum peak-to-trough drawdown as a positive percentage
 * @param totalPnl         total realised profit/loss over the backtest period
 * @param finalEquity      portfolio equity at the end of the backtest
 * @param cagr             compound annual growth rate as a decimal (e.g. 0.15 = 15%)
 * @param avgRMultiple     average R-multiple across all closed trades
 * @param equityCurve      daily equity snapshots ordered chronologically
 * @param trades           all closed trades in the backtest, ordered by exit date
 * @param executionTime    wall-clock time taken to run the backtest
 */
public record BacktestResult(
        int              totalTrades,
        int              winningTrades,
        int              losingTrades,
        double           winRate,
        double           profitFactor,
        double           sharpeRatio,
        double           maxDrawdownPct,
        BigDecimal       totalPnl,
        BigDecimal       finalEquity,
        double           cagr,
        double           avgRMultiple,
        List<EquityPoint> equityCurve,
        List<TradeRecord> trades,
        Duration         executionTime
) {}
