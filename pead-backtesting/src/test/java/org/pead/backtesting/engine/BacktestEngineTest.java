package org.pead.backtesting.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pead.backtesting.engine.model.EarningsEventData;
import org.pead.backtesting.engine.model.PriceBarData;
import org.pead.common.config.StrategyConfig;
import org.pead.common.model.BacktestResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BacktestEngine} with synthetic market data.
 *
 * Scenario: RELIANCE earnings on 2024-01-15 with strong EPS/revenue beat,
 * gap up, high volume. The engine should generate a signal, enter on breakout,
 * and either hit target or stop.
 */
class BacktestEngineTest {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("1000000"); // ₹10 lakh

    private StrategyConfig defaultConfig() {
        return new StrategyConfig(
                3.0,    // minEpsSurprisePct
                2.0,    // minRevenueSurprisePct
                5.0,    // minGapPct
                2.0,    // minRelativeVolume
                60,     // minPeadScore
                0.01,   // maxRiskPctPerTrade (1%)
                5,      // maxConcurrentPositions
                0.02,   // dailyLossLimitPct
                0.10,   // maxDrawdownPct
                0.10,   // maxPositionSizePct
                0.20,   // maxSectorConcentrationPct
                2.0,    // profitTarget1R
                3.0,    // profitTarget2R
                false,  // enableShortSelling
                List.of() // excludedSectors
        );
    }

    @Test
    @DisplayName("Engine should open a LONG trade on RELIANCE earnings gap-up breakout")
    void shouldOpenTradeOnEarningsGapUpBreakout() {
        // Arrange
        StrategyConfig config = defaultConfig();
        BacktestEngine engine = new BacktestEngine(config);

        // Earnings event: RELIANCE, Jan 15 2024, strong beat
        List<EarningsEventData> earnings = List.of(
                new EarningsEventData("RELIANCE", LocalDate.of(2024, 1, 15), 12.0, 8.0)
        );

        // Price bars for RELIANCE: Jan 10 to Feb 15
        List<PriceBarData> bars = buildReliancePriceBars();

        Map<String, List<PriceBarData>> priceData = Map.of("RELIANCE", bars);

        // Act
        BacktestResult result = engine.run(INITIAL_CAPITAL, earnings, priceData);

        // Assert
        assertNotNull(result);
        assertTrue(result.totalTrades() >= 1, "Should have at least 1 trade");
        assertNotNull(result.equityCurve());
        assertFalse(result.equityCurve().isEmpty(), "Equity curve should not be empty");
        assertNotNull(result.trades());
        assertFalse(result.trades().isEmpty(), "Trades list should not be empty");
        assertNotNull(result.executionTime());

        // Verify trade details
        var trade = result.trades().getFirst();
        assertEquals("RELIANCE", trade.ticker());
        assertEquals(org.pead.common.domain.TradeDirection.LONG, trade.direction());
        assertEquals(LocalDate.of(2024, 1, 16), trade.entryDate());
        assertNotNull(trade.exitDate());
        assertTrue(trade.quantity() > 0);

        // Entry should be at 2600 (earnings candle high)
        assertEquals(0, trade.entryPrice().compareTo(new BigDecimal("2600.00")));

        // Verify stats are populated
        assertTrue(result.winRate() >= 0.0 && result.winRate() <= 1.0);
        assertNotNull(result.totalPnl());
        assertNotNull(result.finalEquity());
    }

    @Test
    @DisplayName("Engine should produce valid stats with no earnings events")
    void shouldHandleNoEarningsGracefully() {
        StrategyConfig config = defaultConfig();
        BacktestEngine engine = new BacktestEngine(config);

        List<EarningsEventData> earnings = List.of();
        List<PriceBarData> bars = buildReliancePriceBars();
        Map<String, List<PriceBarData>> priceData = Map.of("RELIANCE", bars);

        BacktestResult result = engine.run(INITIAL_CAPITAL, earnings, priceData);

        assertNotNull(result);
        assertEquals(0, result.totalTrades());
        assertEquals(0, result.winningTrades());
        assertEquals(0, result.losingTrades());
        assertEquals(INITIAL_CAPITAL.doubleValue(), result.finalEquity().doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Engine should handle stop loss correctly")
    void shouldTriggerStopLoss() {
        StrategyConfig config = defaultConfig();
        BacktestEngine engine = new BacktestEngine(config);

        List<EarningsEventData> earnings = List.of(
                new EarningsEventData("RELIANCE", LocalDate.of(2024, 1, 15), 12.0, 8.0)
        );

        // Build bars where price drops to stop after entry
        List<PriceBarData> bars = buildReliancePriceBarsWithStopHit();
        Map<String, List<PriceBarData>> priceData = Map.of("RELIANCE", bars);

        BacktestResult result = engine.run(INITIAL_CAPITAL, earnings, priceData);

        assertNotNull(result);
        assertTrue(result.totalTrades() >= 1);

        var trade = result.trades().getFirst();
        assertEquals("STOP_LOSS", trade.exitReason());
        assertTrue(trade.pnl().signum() < 0, "Stop loss trade should have negative PnL");
        assertTrue(trade.rMultiple() < 0, "Stop loss R-multiple should be negative");
    }

    // -------------------------------------------------------------------------
    // Test data builders
    // -------------------------------------------------------------------------

    /**
     * Builds price bars for RELIANCE from Jan 10 to Feb 15, 2024.
     * Scenario: gap up on Jan 15, breakout on Jan 16, price trends up to hit target2.
     */
    private List<PriceBarData> buildReliancePriceBars() {
        List<PriceBarData> bars = new ArrayList<>();

        // Pre-earnings: Jan 10-14 (normal trading around 2400)
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 10), 2380, 2410, 2370, 2400, 15_000_000, 2390, 2350));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 11), 2400, 2420, 2390, 2410, 12_000_000, 2395, 2355));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 12), 2410, 2430, 2395, 2405, 13_000_000, 2398, 2358));

        // Earnings day: Jan 15 — big gap up, high volume
        // Open=2400 (gap from prev close ~2405), High=2600, Low=2380, Close=2580, Volume=50M
        // Risk = High - Low = 2600 - 2380 = 220
        // Entry = 2600, Stop = 2380, Target1 = 2600 + 2*220 = 3040, Target2 = 2600 + 3*220 = 3260
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 15), 2400, 2600, 2380, 2580, 50_000_000, 2400, 2350));

        // Jan 16: Breakout day — High >= 2600 triggers entry
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 16), 2590, 2650, 2550, 2620, 35_000_000, 2420, 2360));

        // Jan 17-19: Price continues up
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 17), 2620, 2700, 2600, 2680, 28_000_000, 2440, 2370));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 18), 2680, 2750, 2650, 2730, 25_000_000, 2460, 2380));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 19), 2730, 2800, 2700, 2780, 22_000_000, 2480, 2390));

        // Jan 22-26: Further uptrend
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 22), 2780, 2850, 2760, 2830, 20_000_000, 2500, 2400));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 23), 2830, 2900, 2810, 2880, 19_000_000, 2520, 2410));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 24), 2880, 2950, 2860, 2930, 18_000_000, 2540, 2420));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 25), 2930, 3000, 2910, 2980, 17_000_000, 2560, 2430));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 26), 2980, 3050, 2960, 3040, 16_000_000, 2580, 2440));

        // Jan 29-Feb 2: Hits target1 at 3040, then continues to target2
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 29), 3040, 3100, 3020, 3080, 15_000_000, 2600, 2450));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 30), 3080, 3150, 3060, 3130, 14_000_000, 2620, 2460));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 31), 3130, 3200, 3100, 3180, 13_000_000, 2640, 2470));
        bars.add(new PriceBarData(LocalDate.of(2024, 2, 1), 3180, 3270, 3160, 3250, 12_000_000, 2660, 2480));

        // Feb 2: Hits target2 at 3260
        bars.add(new PriceBarData(LocalDate.of(2024, 2, 2), 3250, 3300, 3230, 3280, 11_000_000, 2680, 2490));

        // Post-target bars
        bars.add(new PriceBarData(LocalDate.of(2024, 2, 5), 3280, 3320, 3260, 3300, 10_000_000, 2700, 2500));
        bars.add(new PriceBarData(LocalDate.of(2024, 2, 6), 3300, 3350, 3280, 3330, 9_000_000, 2720, 2510));

        return bars;
    }

    /**
     * Builds price bars where the price drops after entry to hit stop loss.
     */
    private List<PriceBarData> buildReliancePriceBarsWithStopHit() {
        List<PriceBarData> bars = new ArrayList<>();

        // Pre-earnings
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 10), 2380, 2410, 2370, 2400, 15_000_000, 2390, 2350));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 11), 2400, 2420, 2390, 2410, 12_000_000, 2395, 2355));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 12), 2410, 2430, 2395, 2405, 13_000_000, 2398, 2358));

        // Earnings day: same gap up
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 15), 2400, 2600, 2380, 2580, 50_000_000, 2400, 2350));

        // Jan 16: Breakout (entry at 2600)
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 16), 2590, 2650, 2550, 2620, 35_000_000, 2420, 2360));

        // Jan 17: Reversal — price crashes below stop (2380)
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 17), 2600, 2610, 2350, 2370, 40_000_000, 2430, 2365));

        // Remaining bars (post-stop)
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 18), 2370, 2400, 2340, 2360, 30_000_000, 2420, 2360));
        bars.add(new PriceBarData(LocalDate.of(2024, 1, 19), 2360, 2380, 2330, 2350, 25_000_000, 2410, 2355));

        return bars;
    }
}
