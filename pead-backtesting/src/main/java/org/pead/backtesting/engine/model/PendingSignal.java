package org.pead.backtesting.engine.model;

import org.pead.common.domain.TradeDirection;

import java.time.LocalDate;

/**
 * A validated signal awaiting breakout entry confirmation.
 * Expires after a configured number of trading days if no entry triggers.
 *
 * @param ticker      stock symbol
 * @param entryPrice  breakout level (earnings candle high for LONG)
 * @param stopLoss    initial stop-loss level (earnings candle low for LONG)
 * @param target1     first profit target
 * @param target2     second profit target
 * @param peadScore   composite PEAD score at signal time
 * @param expiryDate  last date the signal is valid for entry
 * @param direction   LONG or SHORT
 */
public record PendingSignal(
        String ticker,
        double entryPrice,
        double stopLoss,
        double target1,
        double target2,
        int peadScore,
        LocalDate expiryDate,
        TradeDirection direction
) {}
