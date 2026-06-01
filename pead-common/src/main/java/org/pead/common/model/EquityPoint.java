package org.pead.common.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single point on the equity curve, captured once per trading day.
 *
 * @param date           trading date
 * @param equity         total portfolio equity (cash + open position market value)
 * @param drawdownPct    current drawdown from peak equity, expressed as a positive percentage
 * @param openPositions  number of open positions on this date
 */
public record EquityPoint(
        LocalDate  date,
        BigDecimal equity,
        double     drawdownPct,
        int        openPositions
) {}
