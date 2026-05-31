package org.pead.earnings.service;

import org.pead.common.util.FinancialMath;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates EPS and revenue surprise percentages.
 * surprise% = (actual - estimate) / |estimate| * 100
 */
@Component
public class EarningsSurpriseCalculator {

    private static final int SCALE = 4;

    /**
     * Calculate EPS surprise percentage.
     * @return surprise % as BigDecimal, or null if inputs are null/invalid
     */
    public BigDecimal calculateEpsSurprisePct(BigDecimal epsActual, BigDecimal epsEstimate) {
        if (epsActual == null || epsEstimate == null) {
            return null;
        }
        if (epsEstimate.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        Double surprisePct = FinancialMath.surprisePct(
                epsActual.doubleValue(), epsEstimate.doubleValue());
        if (surprisePct == null) return null;
        return BigDecimal.valueOf(surprisePct).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate revenue surprise percentage.
     */
    public BigDecimal calculateRevenueSurprisePct(Long revenueActual, Long revenueEstimate) {
        if (revenueActual == null || revenueEstimate == null || revenueEstimate == 0L) {
            return null;
        }
        Double surprisePct = FinancialMath.surprisePct(
                revenueActual.doubleValue(), revenueEstimate.doubleValue());
        if (surprisePct == null) return null;
        return BigDecimal.valueOf(surprisePct).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Returns true if EPS actual > estimate (beat).
     */
    public boolean isEpsBeat(BigDecimal epsActual, BigDecimal epsEstimate) {
        if (epsActual == null || epsEstimate == null) return false;
        return epsActual.compareTo(epsEstimate) > 0;
    }

    /**
     * Returns true if revenue actual > estimate (beat).
     */
    public boolean isRevenueBeat(Long revenueActual, Long revenueEstimate) {
        if (revenueActual == null || revenueEstimate == null) return false;
        return revenueActual > revenueEstimate;
    }
}
