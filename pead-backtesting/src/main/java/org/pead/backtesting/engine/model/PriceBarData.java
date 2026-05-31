package org.pead.backtesting.engine.model;

import java.time.LocalDate;

/**
 * Historical OHLCV price bar with pre-computed moving averages.
 *
 * @param date   trading date
 * @param open   opening price
 * @param high   high price
 * @param low    low price
 * @param close  closing price
 * @param volume trading volume
 * @param ema20  20-day exponential moving average
 * @param ema50  50-day exponential moving average
 */
public record PriceBarData(
        LocalDate date,
        double open,
        double high,
        double low,
        double close,
        long volume,
        double ema20,
        double ema50
) {}
