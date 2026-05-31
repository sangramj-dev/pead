package org.pead.backtesting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for running a backtest.
 * All strategy override fields are optional — when null, defaults from StrategyConfig are used.
 *
 * @param startDate      backtest start date
 * @param endDate        backtest end date
 * @param initialCapital starting capital (e.g. 1000000 for ₹10 lakh)
 * @param universeId     stock universe identifier (null = all available tickers)
 * @param tickers        explicit list of tickers to backtest (overrides universeId)
 * @param minEpsSurprisePct       override: minimum EPS surprise %
 * @param minRevenueSurprisePct   override: minimum revenue surprise %
 * @param minGapPct               override: minimum gap %
 * @param minRelativeVolume       override: minimum relative volume
 * @param minPeadScore            override: minimum PEAD score
 * @param maxRiskPctPerTrade      override: max risk % per trade
 * @param maxConcurrentPositions  override: max concurrent positions
 * @param maxPositionSizePct      override: max position size %
 * @param profitTarget1R          override: first profit target R-multiple
 * @param profitTarget2R          override: second profit target R-multiple
 * @param enableShortSelling      override: enable short selling
 */
public record BacktestRequest(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal initialCapital,
        String universeId,
        List<String> tickers,
        Double minEpsSurprisePct,
        Double minRevenueSurprisePct,
        Double minGapPct,
        Double minRelativeVolume,
        Integer minPeadScore,
        Double maxRiskPctPerTrade,
        Integer maxConcurrentPositions,
        Double maxPositionSizePct,
        Double profitTarget1R,
        Double profitTarget2R,
        Boolean enableShortSelling
) {}
