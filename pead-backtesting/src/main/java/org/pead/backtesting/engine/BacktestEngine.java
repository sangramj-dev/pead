package org.pead.backtesting.engine;

import org.pead.backtesting.engine.model.EarningsEventData;
import org.pead.backtesting.engine.model.PendingSignal;
import org.pead.backtesting.engine.model.PriceBarData;
import org.pead.common.config.StrategyConfig;
import org.pead.common.domain.TradeDirection;
import org.pead.common.model.*;
import org.pead.common.strategy.PeadScorer;
import org.pead.common.strategy.PositionSizer;
import org.pead.common.strategy.SignalValidator;
import org.pead.common.strategy.TradeSetupCalculator;
import org.pead.common.strategy.TradeSetupCalculator.TradeSetup;
import org.pead.common.strategy.scoring.PeadScoreResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core backtesting simulation engine. Pure Java — no Spring dependencies.
 *
 * <p>Iterates through trading dates chronologically and simulates:
 * <ol>
 *   <li>Checking pending signals for breakout entries</li>
 *   <li>Monitoring open positions for stop/target hits</li>
 *   <li>Processing new earnings events → score → validate → create pending signal</li>
 *   <li>Recording daily equity curve point</li>
 * </ol>
 */
public final class BacktestEngine {

    private static final int SIGNAL_EXPIRY_DAYS = 5;
    private static final long AVG_VOLUME_PLACEHOLDER = 10_000_000L; // fallback if no history

    private final PeadScorer peadScorer;
    private final SignalValidator signalValidator;
    private final TradeSetupCalculator tradeSetupCalculator;
    private final PositionSizer positionSizer;
    private final StrategyConfig config;

    public BacktestEngine(StrategyConfig config) {
        this.config = config;
        this.peadScorer = new PeadScorer();
        this.signalValidator = new SignalValidator(
                config.minPeadScore(),
                config.minEpsSurprisePct(),
                config.minRevenueSurprisePct(),
                config.minGapPct(),
                config.minRelativeVolume()
                , config.enableShortSelling()
        );
        this.tradeSetupCalculator = new TradeSetupCalculator(
                config.profitTarget1R(),
                config.profitTarget2R()
        );
        this.positionSizer = new PositionSizer(
                config.maxRiskPctPerTrade(),
                config.maxConcurrentPositions(),
                config.maxPositionSizePct()
        );
    }

    /**
     * Run a backtest simulation over the provided historical data.
     *
     * @param initialCapital starting portfolio capital
     * @param earnings       list of earnings events within the backtest period
     * @param priceData      map of ticker → list of price bars (must be sorted by date)
     * @return complete backtest result with stats, trades, and equity curve
     */
    public BacktestResult run(
            BigDecimal initialCapital,
            List<EarningsEventData> earnings,
            Map<String, List<PriceBarData>> priceData) {

        long startTime = System.nanoTime();

        PortfolioState portfolio = new PortfolioState(initialCapital);
        List<PendingSignal> pendingSignals = new ArrayList<>();
        List<EquityPoint> equityCurve = new ArrayList<>();

        // Index earnings by date for fast lookup
        Map<LocalDate, List<EarningsEventData>> earningsByDate = earnings.stream()
                .collect(Collectors.groupingBy(EarningsEventData::date));

        // Index price data by ticker → date for O(1) lookup
        Map<String, Map<LocalDate, PriceBarData>> priceIndex = new HashMap<>();
        for (var entry : priceData.entrySet()) {
            Map<LocalDate, PriceBarData> dateMap = new LinkedHashMap<>();
            for (PriceBarData bar : entry.getValue()) {
                dateMap.put(bar.date(), bar);
            }
            priceIndex.put(entry.getKey(), dateMap);
        }

        // Collect all unique trading dates across all tickers, sorted
        TreeSet<LocalDate> allDates = new TreeSet<>();
        for (List<PriceBarData> bars : priceData.values()) {
            for (PriceBarData bar : bars) {
                allDates.add(bar.date());
            }
        }

        // Day-by-day simulation
        for (LocalDate date : allDates) {

            // Step 1: Check pending signals for breakout entries
            checkPendingSignals(date, pendingSignals, priceIndex, portfolio);

            // Step 2: Monitor open positions for stop/target hits
            monitorOpenPositions(date, priceIndex, portfolio);

            // Step 3: Process new earnings events on this date
            List<EarningsEventData> todayEarnings = earningsByDate.getOrDefault(date, List.of());
            for (EarningsEventData event : todayEarnings) {
                processEarningsEvent(event, date, priceIndex, pendingSignals);
            }

            // Step 4: Remove expired pending signals
            pendingSignals.removeIf(s -> date.isAfter(s.expiryDate()));

            // Step 5: Record equity curve point
            BigDecimal equity = calculateTotalEquity(portfolio, date, priceIndex);
            updatePeakEquity(portfolio, equity);
            double drawdownPct = calculateDrawdown(portfolio, equity);

            equityCurve.add(new EquityPoint(
                    date,
                    equity,
                    drawdownPct,
                    portfolio.getOpenPositions().size()
            ));
        }

        Duration executionTime = Duration.ofNanos(System.nanoTime() - startTime);

        return StatsCalculator.calculate(portfolio, equityCurve, initialCapital, executionTime);
    }

    private void checkPendingSignals(
            LocalDate date,
            List<PendingSignal> pendingSignals,
            Map<String, Map<LocalDate, PriceBarData>> priceIndex,
            PortfolioState portfolio) {

        Iterator<PendingSignal> it = pendingSignals.iterator();
        while (it.hasNext()) {
            PendingSignal signal = it.next();

            Map<LocalDate, PriceBarData> tickerBars = priceIndex.get(signal.ticker());
            if (tickerBars == null) continue;

            PriceBarData bar = tickerBars.get(date);
            if (bar == null) continue;

            // For LONG: entry triggers if high >= entry price
            boolean triggered = switch (signal.direction()) {
                case LONG -> bar.high() >= signal.entryPrice();
                case SHORT -> bar.low() <= signal.entryPrice();
            };

            if (triggered && positionSizer.canOpenPosition(portfolio.getOpenPositions().size())) {
                // Check not already holding this ticker
                boolean alreadyHolding = portfolio.getOpenPositions().stream()
                        .anyMatch(p -> p.ticker().equals(signal.ticker()));
                if (alreadyHolding) continue;

                // Calculate position size
                double equity = portfolio.getCash().doubleValue() + getOpenPositionsValue(portfolio, date, priceIndex);
                int shares = positionSizer.calculateShares(equity, signal.entryPrice(), signal.stopLoss());
                if (shares <= 0) continue;

                // Check sufficient cash
                BigDecimal cost = BigDecimal.valueOf(signal.entryPrice())
                        .multiply(BigDecimal.valueOf(shares))
                        .setScale(2, RoundingMode.HALF_UP);
                if (cost.compareTo(portfolio.getCash()) > 0) continue;

                // Open position
                portfolio.setCash(portfolio.getCash().subtract(cost));
                portfolio.getOpenPositions().add(new PortfolioState.OpenPosition(
                        signal.ticker(),
                        signal.direction(),
                        shares,
                        BigDecimal.valueOf(signal.entryPrice()).setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(signal.stopLoss()).setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(signal.target1()).setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(signal.target2()).setScale(2, RoundingMode.HALF_UP),
                        date,
                        signal.peadScore(),
                        BigDecimal.valueOf(signal.entryPrice()).setScale(2, RoundingMode.HALF_UP),
                        false
                ));

                it.remove();
            }
        }
    }

    private void monitorOpenPositions(
            LocalDate date,
            Map<String, Map<LocalDate, PriceBarData>> priceIndex,
            PortfolioState portfolio) {

        Iterator<PortfolioState.OpenPosition> it = portfolio.getOpenPositions().iterator();
        List<PortfolioState.OpenPosition> updatedPositions = new ArrayList<>();
        List<PortfolioState.OpenPosition> toRemove = new ArrayList<>();

        while (it.hasNext()) {
            PortfolioState.OpenPosition pos = it.next();

            Map<LocalDate, PriceBarData> tickerBars = priceIndex.get(pos.ticker());
            if (tickerBars == null) continue;

            PriceBarData bar = tickerBars.get(date);
            if (bar == null) continue;

            if (pos.direction() == TradeDirection.LONG) {
                // Check stop loss hit
                if (bar.low() <= pos.stopLoss().doubleValue()) {
                    closeTrade(pos, pos.stopLoss(), date, "STOP_LOSS", portfolio);
                    toRemove.add(pos);
                }
                // Check target2 hit
                else if (bar.high() >= pos.target2().doubleValue()) {
                    closeTrade(pos, pos.target2(), date, "TARGET_2", portfolio);
                    toRemove.add(pos);
                }
                // Check target1 hit (trail stop to entry)
                else if (!pos.target1Hit() && bar.high() >= pos.target1().doubleValue()) {
                    // Mark target1 hit and trail stop to entry
                    PortfolioState.OpenPosition updated = new PortfolioState.OpenPosition(
                            pos.ticker(), pos.direction(), pos.quantity(),
                            pos.entryPrice(), pos.entryPrice(), // stop trailed to entry
                            pos.target1(), pos.target2(), pos.entryDate(),
                            pos.peadScore(),
                            BigDecimal.valueOf(bar.close()).setScale(2, RoundingMode.HALF_UP),
                            true
                    );
                    updatedPositions.add(updated);
                    toRemove.add(pos);
                } else {
                    // Update current price
                    PortfolioState.OpenPosition updated = new PortfolioState.OpenPosition(
                            pos.ticker(), pos.direction(), pos.quantity(),
                            pos.entryPrice(), pos.stopLoss(),
                            pos.target1(), pos.target2(), pos.entryDate(),
                            pos.peadScore(),
                            BigDecimal.valueOf(bar.close()).setScale(2, RoundingMode.HALF_UP),
                            pos.target1Hit()
                    );
                    updatedPositions.add(updated);
                    toRemove.add(pos);
                }
            } else {
                // SHORT position logic (mirror of LONG)
                if (bar.high() >= pos.stopLoss().doubleValue()) {
                    closeTrade(pos, pos.stopLoss(), date, "STOP_LOSS", portfolio);
                    toRemove.add(pos);
                } else if (bar.low() <= pos.target2().doubleValue()) {
                    closeTrade(pos, pos.target2(), date, "TARGET_2", portfolio);
                    toRemove.add(pos);
                } else if (!pos.target1Hit() && bar.low() <= pos.target1().doubleValue()) {
                    PortfolioState.OpenPosition updated = new PortfolioState.OpenPosition(
                            pos.ticker(), pos.direction(), pos.quantity(),
                            pos.entryPrice(), pos.entryPrice(),
                            pos.target1(), pos.target2(), pos.entryDate(),
                            pos.peadScore(),
                            BigDecimal.valueOf(bar.close()).setScale(2, RoundingMode.HALF_UP),
                            true
                    );
                    updatedPositions.add(updated);
                    toRemove.add(pos);
                } else {
                    PortfolioState.OpenPosition updated = new PortfolioState.OpenPosition(
                            pos.ticker(), pos.direction(), pos.quantity(),
                            pos.entryPrice(), pos.stopLoss(),
                            pos.target1(), pos.target2(), pos.entryDate(),
                            pos.peadScore(),
                            BigDecimal.valueOf(bar.close()).setScale(2, RoundingMode.HALF_UP),
                            pos.target1Hit()
                    );
                    updatedPositions.add(updated);
                    toRemove.add(pos);
                }
            }
        }

        portfolio.getOpenPositions().removeAll(toRemove);
        portfolio.getOpenPositions().addAll(updatedPositions);
    }

    private void closeTrade(
            PortfolioState.OpenPosition pos,
            BigDecimal exitPrice,
            LocalDate exitDate,
            String exitReason,
            PortfolioState portfolio) {

        BigDecimal pnl;
        if (pos.direction() == TradeDirection.LONG) {
            pnl = exitPrice.subtract(pos.entryPrice())
                    .multiply(BigDecimal.valueOf(pos.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            pnl = pos.entryPrice().subtract(exitPrice)
                    .multiply(BigDecimal.valueOf(pos.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Calculate R-multiple
        BigDecimal risk = pos.entryPrice().subtract(pos.stopLoss()).abs();
        double rMultiple = risk.signum() == 0 ? 0.0 :
                pnl.doubleValue() / (risk.doubleValue() * pos.quantity());

        // Return proceeds to cash
        BigDecimal proceeds = exitPrice.multiply(BigDecimal.valueOf(pos.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
        portfolio.setCash(portfolio.getCash().add(proceeds));

        // Record closed trade
        portfolio.getClosedTrades().add(new TradeRecord(
                pos.ticker(),
                pos.direction(),
                pos.entryDate(),
                exitDate,
                pos.entryPrice(),
                exitPrice,
                pos.quantity(),
                pnl,
                rMultiple,
                pos.peadScore(),
                exitReason
        ));
    }

    private void processEarningsEvent(
            EarningsEventData event,
            LocalDate date,
            Map<String, Map<LocalDate, PriceBarData>> priceIndex,
            List<PendingSignal> pendingSignals) {

        Map<LocalDate, PriceBarData> tickerBars = priceIndex.get(event.ticker());
        if (tickerBars == null) return;

        PriceBarData bar = tickerBars.get(date);
        if (bar == null) return;

        // Calculate gap percentage: earnings day close vs previous close
        // This captures the full earnings-day price reaction (PEAD gap)
        double prevClose = findPreviousClose(tickerBars, date);
        if (prevClose <= 0) {
            prevClose = bar.ema20() > 0 ? bar.ema20() : bar.open();
        }
        double gapPct = ((bar.close() - prevClose) / prevClose) * 100.0;
        double absGapPct = Math.abs(gapPct);

        // Calculate relative volume (vs placeholder average)
        double relativeVolume = (double) bar.volume() / AVG_VOLUME_PLACEHOLDER;

        // Determine if price is above EMA indicators
        boolean aboveEma20 = bar.close() > bar.ema20();
        boolean aboveEma50 = bar.close() > bar.ema50();

        // Score the earnings event
        PeadScoreResult scoreResult = peadScorer.score(
                Math.abs(event.epsSurprisePct()),
                Math.abs(event.revenueSurprisePct()),
                absGapPct,
                relativeVolume,
                aboveEma20,
                aboveEma50,
                bar.close(),
                bar.high()
        );

        // Validate signal
        boolean validLong = event.epsSurprisePct() > 0 && gapPct > 0 &&
                signalValidator.isValidLong(scoreResult,
                        event.epsSurprisePct(), event.revenueSurprisePct(), absGapPct, relativeVolume);

        boolean validShort = event.epsSurprisePct() < 0 && gapPct < 0 &&
                signalValidator.isValidShort(scoreResult,
                        Math.abs(event.epsSurprisePct()), Math.abs(event.revenueSurprisePct()),
                        absGapPct, relativeVolume);

        if (validLong) {
            TradeSetup setup = tradeSetupCalculator.calculate(bar.high(), bar.low());
            pendingSignals.add(new PendingSignal(
                    event.ticker(),
                    setup.entryPrice().doubleValue(),
                    setup.stopLoss().doubleValue(),
                    setup.target1().doubleValue(),
                    setup.target2().doubleValue(),
                    scoreResult.totalScore(),
                    date.plusDays(SIGNAL_EXPIRY_DAYS),
                    TradeDirection.LONG
            ));
        } else if (validShort) {
            // For SHORT: entry at candle low, stop at candle high
            TradeSetup setup = tradeSetupCalculator.calculate(bar.high(), bar.low());
            pendingSignals.add(new PendingSignal(
                    event.ticker(),
                    setup.stopLoss().doubleValue(), // entry at low for short
                    setup.entryPrice().doubleValue(), // stop at high for short
                    setup.stopLoss().subtract(
                            setup.entryPrice().subtract(setup.stopLoss())
                                    .multiply(BigDecimal.valueOf(config.profitTarget1R()))
                    ).doubleValue(),
                    setup.stopLoss().subtract(
                            setup.entryPrice().subtract(setup.stopLoss())
                                    .multiply(BigDecimal.valueOf(config.profitTarget2R()))
                    ).doubleValue(),
                    scoreResult.totalScore(),
                    date.plusDays(SIGNAL_EXPIRY_DAYS),
                    TradeDirection.SHORT
            ));
        }
    }

    /**
     * Find the previous trading day's close for a given ticker and date.
     */
    private double findPreviousClose(Map<LocalDate, PriceBarData> tickerBars, LocalDate date) {
        // Search backwards up to 10 calendar days for previous bar
        for (int i = 1; i <= 10; i++) {
            LocalDate prevDate = date.minusDays(i);
            PriceBarData prevBar = tickerBars.get(prevDate);
            if (prevBar != null) {
                return prevBar.close();
            }
        }
        return -1.0;
    }

    private BigDecimal calculateTotalEquity(
            PortfolioState portfolio,
            LocalDate date,
            Map<String, Map<LocalDate, PriceBarData>> priceIndex) {

        BigDecimal positionsValue = BigDecimal.ZERO;
        for (PortfolioState.OpenPosition pos : portfolio.getOpenPositions()) {
            Map<LocalDate, PriceBarData> tickerBars = priceIndex.get(pos.ticker());
            if (tickerBars != null) {
                PriceBarData bar = tickerBars.get(date);
                if (bar != null) {
                    positionsValue = positionsValue.add(
                            BigDecimal.valueOf(bar.close())
                                    .multiply(BigDecimal.valueOf(pos.quantity()))
                                    .setScale(2, RoundingMode.HALF_UP)
                    );
                } else {
                    // Use last known price
                    positionsValue = positionsValue.add(
                            pos.currentPrice().multiply(BigDecimal.valueOf(pos.quantity()))
                    );
                }
            }
        }
        return portfolio.getCash().add(positionsValue);
    }

    private double getOpenPositionsValue(
            PortfolioState portfolio,
            LocalDate date,
            Map<String, Map<LocalDate, PriceBarData>> priceIndex) {

        double value = 0.0;
        for (PortfolioState.OpenPosition pos : portfolio.getOpenPositions()) {
            Map<LocalDate, PriceBarData> tickerBars = priceIndex.get(pos.ticker());
            if (tickerBars != null) {
                PriceBarData bar = tickerBars.get(date);
                if (bar != null) {
                    value += bar.close() * pos.quantity();
                } else {
                    value += pos.currentPrice().doubleValue() * pos.quantity();
                }
            }
        }
        return value;
    }

    private void updatePeakEquity(PortfolioState portfolio, BigDecimal equity) {
        if (equity.compareTo(portfolio.getPeakEquity()) > 0) {
            portfolio.setPeakEquity(equity);
        }
    }

    private double calculateDrawdown(PortfolioState portfolio, BigDecimal equity) {
        if (portfolio.getPeakEquity().signum() == 0) return 0.0;
        BigDecimal drawdown = portfolio.getPeakEquity().subtract(equity);
        return drawdown.doubleValue() / portfolio.getPeakEquity().doubleValue() * 100.0;
    }
}
