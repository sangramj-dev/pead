package org.pead.backtesting.engine;

import org.pead.common.model.BacktestResult;
import org.pead.common.model.EquityPoint;
import org.pead.common.model.PortfolioState;
import org.pead.common.model.TradeRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes BacktestResult statistics from completed portfolio state and equity curve.
 * Pure Java — no Spring dependencies.
 */
public final class StatsCalculator {

    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final double SQRT_252 = Math.sqrt(TRADING_DAYS_PER_YEAR);

    private StatsCalculator() {} // utility class

    /**
     * Calculate comprehensive backtest statistics.
     *
     * @param portfolio      final portfolio state with closed trades
     * @param equityCurve    daily equity snapshots
     * @param initialCapital starting capital
     * @param executionTime  wall-clock time of backtest execution
     * @return immutable BacktestResult
     */
    public static BacktestResult calculate(
            PortfolioState portfolio,
            List<EquityPoint> equityCurve,
            BigDecimal initialCapital,
            Duration executionTime) {

        List<TradeRecord> trades = portfolio.getClosedTrades();
        int totalTrades = trades.size();
        int winningTrades = (int) trades.stream().filter(t -> t.pnl().signum() > 0).count();
        int losingTrades = (int) trades.stream().filter(t -> t.pnl().signum() < 0).count();

        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades : 0.0;
        double profitFactor = calculateProfitFactor(trades);
        double sharpeRatio = calculateSharpeRatio(equityCurve);
        double maxDrawdownPct = calculateMaxDrawdown(equityCurve);

        BigDecimal totalPnl = trades.stream()
                .map(TradeRecord::pnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalEquity = equityCurve.isEmpty() ? initialCapital :
                equityCurve.getLast().equity();

        double cagr = calculateCagr(initialCapital, finalEquity, equityCurve);
        double avgRMultiple = trades.isEmpty() ? 0.0 :
                trades.stream().mapToDouble(TradeRecord::rMultiple).average().orElse(0.0);

        return new BacktestResult(
                totalTrades,
                winningTrades,
                losingTrades,
                winRate,
                profitFactor,
                sharpeRatio,
                maxDrawdownPct,
                totalPnl,
                finalEquity,
                cagr,
                avgRMultiple,
                equityCurve,
                trades,
                executionTime
        );
    }

    private static double calculateProfitFactor(List<TradeRecord> trades) {
        double grossProfit = trades.stream()
                .filter(t -> t.pnl().signum() > 0)
                .mapToDouble(t -> t.pnl().doubleValue())
                .sum();
        double grossLoss = trades.stream()
                .filter(t -> t.pnl().signum() < 0)
                .mapToDouble(t -> Math.abs(t.pnl().doubleValue()))
                .sum();
        return grossLoss == 0.0 ? (grossProfit > 0 ? Double.MAX_VALUE : 0.0) : grossProfit / grossLoss;
    }

    /**
     * Annualized Sharpe ratio from daily equity returns.
     * Formula: (mean(dailyReturns) / std(dailyReturns)) * sqrt(252)
     */
    private static double calculateSharpeRatio(List<EquityPoint> equityCurve) {
        if (equityCurve.size() < 2) return 0.0;

        List<Double> dailyReturns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            double prevEquity = equityCurve.get(i - 1).equity().doubleValue();
            double currEquity = equityCurve.get(i).equity().doubleValue();
            if (prevEquity > 0) {
                dailyReturns.add((currEquity - prevEquity) / prevEquity);
            }
        }

        if (dailyReturns.isEmpty()) return 0.0;

        double meanReturn = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = dailyReturns.stream()
                .mapToDouble(r -> (r - meanReturn) * (r - meanReturn))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        return stdDev == 0.0 ? 0.0 : (meanReturn / stdDev) * SQRT_252;
    }

    private static double calculateMaxDrawdown(List<EquityPoint> equityCurve) {
        return equityCurve.stream()
                .mapToDouble(EquityPoint::drawdownPct)
                .max()
                .orElse(0.0);
    }

    /**
     * Compound Annual Growth Rate.
     * Formula: (finalEquity / initialCapital)^(1/years) - 1
     */
    private static double calculateCagr(
            BigDecimal initialCapital,
            BigDecimal finalEquity,
            List<EquityPoint> equityCurve) {

        if (equityCurve.size() < 2 || initialCapital.signum() <= 0) return 0.0;

        long days = ChronoUnit.DAYS.between(
                equityCurve.getFirst().date(),
                equityCurve.getLast().date()
        );
        if (days <= 0) return 0.0;

        double years = days / 365.25;
        double ratio = finalEquity.doubleValue() / initialCapital.doubleValue();

        if (ratio <= 0) return -1.0;
        return Math.pow(ratio, 1.0 / years) - 1.0;
    }
}
