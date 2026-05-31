package org.pead.backtesting.engine.model;

import java.time.LocalDate;

/**
 * Historical earnings event data for backtesting.
 *
 * @param ticker            stock symbol (e.g. "RELIANCE")
 * @param date              earnings announcement date
 * @param epsSurprisePct    EPS beat percentage (positive = beat, negative = miss)
 * @param revenueSurprisePct revenue beat percentage (positive = beat, negative = miss)
 */
public record EarningsEventData(
        String ticker,
        LocalDate date,
        double epsSurprisePct,
        double revenueSurprisePct
) {}
