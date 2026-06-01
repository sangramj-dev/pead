package org.pead.backtesting.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pead.backtesting.engine.BacktestEngine;
import org.pead.backtesting.engine.model.EarningsEventData;
import org.pead.backtesting.engine.model.PriceBarData;
import org.pead.common.config.StrategyConfig;
import org.pead.common.model.BacktestResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying the full backtest flow with realistic multi-quarter data.
 * Does NOT require running infrastructure (DB, Kafka) — tests engine logic with
 * synthetic data representing real-world Indian stock earnings scenarios.
 *
 * Run: mvn test -pl pead-backtesting -Dtest="BacktestIntegrationTest"
 */
class BacktestIntegrationTest {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("1000000"); // ₹10 lakh

    private StrategyConfig defaultConfig() {
        return new StrategyConfig(
                3.0, 2.0, 5.0, 2.0, 60,
                0.01, 5, 0.02, 0.10, 0.10, 0.20,
                2.0, 3.0, false, List.of()
        );
    }

    @Test
    @DisplayName("Full backtest with multiple quarterly earnings over 1 year")
    void fullBacktestWithMultipleEarningsEvents() {
        BacktestEngine engine = new BacktestEngine(defaultConfig());

        // Simulate RELIANCE with 3 quarterly earnings — all strong beats
        List<EarningsEventData> earnings = List.of(
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 1, 20), 8.4, 5.5),
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 4, 21), 5.2, 3.8),
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 7, 21), 12.0, 7.5)
        );

        Map<String, List<PriceBarData>> priceData = Map.of(
                "RELIANCE", generateYearOfPriceBars(2400, 2023)
        );

        BacktestResult result = engine.run(INITIAL_CAPITAL, earnings, priceData);

        // Basic assertions
        assertNotNull(result);
        assertTrue(result.totalTrades() >= 1, "Should have at least 1 trade from strong earnings");
        assertNotNull(result.equityCurve());
        assertFalse(result.equityCurve().isEmpty(), "Equity curve should have data points");
        assertTrue(result.executionTime().toMillis() < 5000, "Should complete in under 5 seconds");

        // Stats validation
        if (result.totalTrades() > 0) {
            assertTrue(result.winRate() >= 0 && result.winRate() <= 1.0);
            assertTrue(result.maxDrawdownPct() >= 0);
            assertNotNull(result.totalPnl());
            assertNotNull(result.finalEquity());
            assertEquals(result.winningTrades() + result.losingTrades(), result.totalTrades());
        }

        printResults("Multi-Quarter RELIANCE", result);
    }

    @Test
    @DisplayName("Backtest with multiple tickers in same period")
    void backtestWithMultipleTickers() {
        BacktestEngine engine = new BacktestEngine(defaultConfig());

        // 3 different stocks reporting on different days
        List<EarningsEventData> earnings = List.of(
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 1, 20), 10.0, 6.0),
                new EarningsEventData("TCS", LocalDate.of(2023, 1, 12), 8.0, 5.0),
                new EarningsEventData("INFY", LocalDate.of(2023, 1, 25), 7.0, 4.0)
        );

        // Use deterministic bars that guarantee valid signals and entry triggers.
        // End date is far enough for the 2R target to be hit (~25 trading days at 0.5%/day drift).
        Map<String, List<PriceBarData>> priceData = Map.of(
                "RELIANCE", buildDeterministicBars(2500, LocalDate.of(2023, 1, 2),
                        LocalDate.of(2023, 1, 20), LocalDate.of(2023, 4, 30)),
                "TCS", buildDeterministicBars(3400, LocalDate.of(2023, 1, 2),
                        LocalDate.of(2023, 1, 12), LocalDate.of(2023, 4, 30)),
                "INFY", buildDeterministicBars(1500, LocalDate.of(2023, 1, 2),
                        LocalDate.of(2023, 1, 25), LocalDate.of(2023, 4, 30))
        );

        BacktestResult result = engine.run(INITIAL_CAPITAL, earnings, priceData);

        assertNotNull(result);
        assertTrue(result.totalTrades() >= 1, "Should have trades from at least one ticker");

        // Verify portfolio constraint: max 5 concurrent positions
        result.equityCurve().forEach(ep ->
                assertTrue(ep.openPositions() <= 5, "Should respect max concurrent positions"));

        printResults("Multi-Ticker (RELIANCE, TCS, INFY)", result);
    }

    @Test
    @DisplayName("Backtest with weak earnings should generate fewer or no trades")
    void weakEarningsShouldNotGenerateSignals() {
        BacktestEngine engine = new BacktestEngine(defaultConfig());

        // Weak earnings: below threshold (eps=2%, rev=1%)
        List<EarningsEventData> earnings = List.of(
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 1, 20), 2.0, 1.0)
        );

        Map<String, List<PriceBarData>> priceData = Map.of(
                "RELIANCE", generateQuarterPriceBars(2500, LocalDate.of(2023, 1, 2), 0.03)
        );

        BacktestResult result = engine.run(INITIAL_CAPITAL, earnings, priceData);

        assertNotNull(result);
        // With 2% EPS (below 3% threshold) and 3% gap (below 5% threshold),
        // signal should NOT validate — 0 trades expected
        assertEquals(0, result.totalTrades(), "Weak earnings should not generate valid signals");
        assertEquals(INITIAL_CAPITAL.doubleValue(), result.finalEquity().doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Performance: 1 year of data with 8 earnings events completes quickly")
    void performanceBenchmark() {
        BacktestEngine engine = new BacktestEngine(defaultConfig());

        // Simulate 2 stocks x 4 quarters = 8 earnings events
        List<EarningsEventData> earnings = List.of(
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 1, 20), 10.0, 6.0),
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 4, 21), 8.0, 5.0),
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 7, 21), 12.0, 7.0),
                new EarningsEventData("RELIANCE", LocalDate.of(2023, 10, 20), 9.0, 5.5),
                new EarningsEventData("TCS", LocalDate.of(2023, 1, 9), 7.0, 4.0),
                new EarningsEventData("TCS", LocalDate.of(2023, 4, 12), 6.0, 3.5),
                new EarningsEventData("TCS", LocalDate.of(2023, 7, 12), 9.0, 5.5),
                new EarningsEventData("TCS", LocalDate.of(2023, 10, 11), 8.0, 5.0)
        );

        Map<String, List<PriceBarData>> priceData = Map.of(
                "RELIANCE", generateYearOfPriceBars(2500, 2023),
                "TCS", generateYearOfPriceBars(3400, 2023)
        );

        long start = System.currentTimeMillis();
        BacktestResult result = engine.run(INITIAL_CAPITAL, earnings, priceData);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertTrue(elapsed < 2000, "8-event backtest should complete in under 2 seconds, took " + elapsed + "ms");
        assertTrue(result.totalTrades() >= 1, "Should generate at least 1 trade from 8 strong earnings");

        printResults("Performance (8 events, " + elapsed + "ms)", result);
    }

    // -------------------------------------------------------------------------
    // Data generators
    // -------------------------------------------------------------------------

    private List<PriceBarData> generateYearOfPriceBars(double startPrice, int year) {
        List<PriceBarData> bars = new ArrayList<>();
        double price = startPrice;
        LocalDate date = LocalDate.of(year, 1, 2);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        Random rng = new Random(42 + year);

        // Earnings dates for gap-up simulation
        Set<LocalDate> earningsDates = Set.of(
                LocalDate.of(year, 1, 9), LocalDate.of(year, 1, 12),
                LocalDate.of(year, 1, 20), LocalDate.of(year, 1, 25),
                LocalDate.of(year, 4, 12), LocalDate.of(year, 4, 21),
                LocalDate.of(year, 7, 12), LocalDate.of(year, 7, 21),
                LocalDate.of(year, 10, 11), LocalDate.of(year, 10, 20)
        );

        while (!date.isAfter(endDate)) {
            if (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
                continue;
            }

            double dailyReturn;
            long volume = 5_000_000 + rng.nextInt(10_000_000);

            if (earningsDates.contains(date)) {
                dailyReturn = 0.06 + rng.nextDouble() * 0.04; // 6-10% gap up
                volume *= 4; // Volume spike
            } else {
                dailyReturn = rng.nextGaussian() * 0.015; // Normal volatility
            }

            double open = price * (1 + dailyReturn * 0.3);
            double high = Math.max(open, price * (1 + dailyReturn)) * (1 + rng.nextDouble() * 0.008);
            double low = Math.min(open, price * (1 + dailyReturn)) * (1 - rng.nextDouble() * 0.008);
            double close = price * (1 + dailyReturn);

            if (high < close) high = close * 1.002;
            if (low > close) low = close * 0.998;
            if (low > open) low = open * 0.998;
            if (high < open) high = open * 1.002;

            double ema20 = price * (1 - 0.02 + rng.nextDouble() * 0.01);
            double ema50 = price * (1 - 0.05 + rng.nextDouble() * 0.02);

            bars.add(new PriceBarData(date, open, high, low, close, volume, ema20, ema50));
            price = close;
            date = date.plusDays(1);
        }
        return bars;
    }

    private List<PriceBarData> generateQuarterPriceBars(double startPrice, LocalDate startDate, double earningsGap) {
        List<PriceBarData> bars = new ArrayList<>();
        double price = startPrice;
        LocalDate date = startDate;
        LocalDate endDate = startDate.plusMonths(3);
        Random rng = new Random((long)(startPrice * 100));

        // Put earnings gap on various dates
        Set<LocalDate> earningsDates = Set.of(
                startDate.plusDays(8), startDate.plusDays(10),
                startDate.plusDays(18), startDate.plusDays(23)
        );

        while (!date.isAfter(endDate)) {
            if (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
                continue;
            }

            double dailyReturn;
            long volume = 3_000_000 + rng.nextInt(5_000_000);

            if (earningsDates.contains(date)) {
                dailyReturn = earningsGap;
                volume *= 3;
            } else {
                dailyReturn = rng.nextGaussian() * 0.012;
            }

            double open = price * (1 + dailyReturn * 0.4);
            double high = Math.max(open, price * (1 + dailyReturn)) * (1 + rng.nextDouble() * 0.006);
            double low = Math.min(open, price * (1 + dailyReturn)) * (1 - rng.nextDouble() * 0.005);
            double close = price * (1 + dailyReturn);

            if (high < close) high = close * 1.001;
            if (low > close) low = close * 0.999;
            if (low > open) low = open * 0.999;
            if (high < open) high = open * 1.001;

            double ema20 = price * 0.99;
            double ema50 = price * 0.97;

            bars.add(new PriceBarData(date, open, high, low, close, volume, ema20, ema50));
            price = close;
            date = date.plusDays(1);
        }
        return bars;
    }

    /**
     * Build deterministic price bars with an explicit gap-up on the earnings date
     * and a breakout bar the next trading day to guarantee entry triggers.
     * Post-earnings drift is 0.5%/day — enough to hit the 2R target within ~30 days.
     *
     * <p>Trade setup for an 8% gap candle (e.g. entry=1080, stop=1010, risk=70):
     * <ul>
     *   <li>Target1 = entry + 2R = 1080 + 140 = 1220 (~13% above entry)</li>
     *   <li>At 0.5%/day drift, high reaches +13% in ~25 trading days</li>
     * </ul>
     */
    private List<PriceBarData> buildDeterministicBars(
            double startPrice, LocalDate startDate, LocalDate earningsDate, LocalDate endDate) {

        List<PriceBarData> bars = new ArrayList<>();
        double price = startPrice;
        LocalDate date = startDate;
        double earningsHigh = 0;

        while (!date.isAfter(endDate)) {
            if (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
                continue;
            }

            double open, high, low, close;
            long volume;

            if (date.equals(earningsDate)) {
                // Earnings day: 8% gap-up with high volume
                open  = price * 1.02;       // small gap-up open
                close = price * 1.08;       // full 8% gap for the day (close vs prev close)
                high  = close * 1.003;      // high just above close (strong close)
                low   = price * 1.01;       // low near previous close
                volume = 25_000_000;        // 2.5x relative volume (AVG=10M)
                earningsHigh = high;
            } else if (earningsHigh > 0 && date.isAfter(earningsDate)) {
                // Post-earnings: strong upward drift (0.5%/day) so targets are reachable.
                // This simulates real PEAD momentum continuation.
                open  = price * 1.003;
                close = price * 1.005;       // 0.5% daily drift
                high  = close * 1.004;       // high slightly above close
                low   = price * 0.999;       // tight low (won't hit stop)
                volume = 12_000_000;
            } else {
                // Pre-earnings: small drift up
                open  = price * 1.001;
                close = price * 1.002;
                high  = price * 1.005;
                low   = price * 0.998;
                volume = 8_000_000;
            }

            // Ensure OHLC consistency
            if (high < Math.max(open, close)) high = Math.max(open, close) * 1.001;
            if (low > Math.min(open, close)) low = Math.min(open, close) * 0.999;

            // EMA values: below price to ensure trend is up
            double ema20 = price * 0.97;
            double ema50 = price * 0.94;

            bars.add(new PriceBarData(date, open, high, low, close, volume, ema20, ema50));
            price = close;
            date = date.plusDays(1);
        }
        return bars;
    }

    private void printResults(String testName, BacktestResult result) {
        System.out.println("=== " + testName + " ===");
        System.out.println("  Trades: " + result.totalTrades() +
                " (W:" + result.winningTrades() + " L:" + result.losingTrades() + ")");
        System.out.printf("  Win rate: %.1f%%%n", result.winRate() * 100);
        System.out.printf("  Total P&L: ₹%.2f%n", result.totalPnl());
        System.out.printf("  Final equity: ₹%.2f%n", result.finalEquity());
        System.out.printf("  Sharpe: %.2f | CAGR: %.1f%% | Max DD: %.1f%%%n",
                result.sharpeRatio(), result.cagr(), result.maxDrawdownPct());
        System.out.printf("  Profit factor: %.2f | Avg R: %.2f%n",
                result.profitFactor(), result.avgRMultiple());
        System.out.printf("  Execution: %dms%n", result.executionTime().toMillis());
        System.out.println();
    }
}
