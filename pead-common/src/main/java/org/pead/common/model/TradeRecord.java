package org.pead.common.model;

import org.pead.common.domain.TradeDirection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable record of a completed trade (entry to exit).
 *
 * @param ticker      trading symbol
 * @param direction   LONG or SHORT
 * @param entryDate   date the position was opened
 * @param exitDate    date the position was closed
 * @param entryPrice  price per share at entry
 * @param exitPrice   price per share at exit
 * @param quantity    number of shares traded
 * @param pnl         realised profit/loss in currency units
 * @param rMultiple   P&L expressed as a multiple of initial risk (e.g. 2.0 = 2R win)
 * @param peadScore   composite PEAD score at the time the signal was generated
 * @param exitReason  human-readable reason for exit (e.g. "TARGET_1", "STOP_LOSS", "EOD")
 */
public record TradeRecord(
        String        ticker,
        TradeDirection direction,
        LocalDate     entryDate,
        LocalDate     exitDate,
        BigDecimal    entryPrice,
        BigDecimal    exitPrice,
        int           quantity,
        BigDecimal    pnl,
        double        rMultiple,
        int           peadScore,
        String        exitReason
) {}
