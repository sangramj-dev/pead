package org.pead.common.strategy;

import org.pead.common.strategy.scoring.PeadScoreResult;

/**
 * Validates whether a scored signal meets the minimum thresholds for a trade.
 * Pure Java — no Spring dependencies; instantiable with {@code new}.
 *
 * <p>Typical usage:
 * <pre>
 *   SignalValidator validator = new SignalValidator(60, 3.0, 2.0, 5.0, 2.0, false);
 *   if (validator.isValidLong(result, epsSurprisePct, revenueSurprisePct, gapPct, relVol)) { ... }
 * </pre>
 */
public final class SignalValidator {

    private final int    minPeadScore;
    private final double minEpsSurprisePct;
    private final double minRevenueSurprisePct;
    private final double minGapPct;
    private final double minRelativeVolume;
    private final boolean enableShortSelling;

    /**
     * @param minPeadScore          minimum composite score required (e.g. 60)
     * @param minEpsSurprisePct     minimum EPS beat % required
     * @param minRevenueSurprisePct minimum revenue beat % required
     * @param minGapPct             minimum absolute gap % required
     * @param minRelativeVolume     minimum relative volume required
     * @param enableShortSelling    whether short signals are permitted
     */
    public SignalValidator(
            int minPeadScore,
            double minEpsSurprisePct,
            double minRevenueSurprisePct,
            double minGapPct,
            double minRelativeVolume,
            boolean enableShortSelling) {
        this.minPeadScore          = minPeadScore;
        this.minEpsSurprisePct     = minEpsSurprisePct;
        this.minRevenueSurprisePct = minRevenueSurprisePct;
        this.minGapPct             = minGapPct;
        this.minRelativeVolume     = minRelativeVolume;
        this.enableShortSelling    = enableShortSelling;
    }

    /**
     * Validate a long signal: gap up with EPS + revenue beat, score passes threshold.
     *
     * @param result             pre-computed PEAD score
     * @param epsSurprisePct     EPS beat percentage (must be positive)
     * @param revenueSurprisePct revenue beat percentage (must be positive)
     * @param absGapPct          absolute gap percentage (positive = gap up)
     * @param relativeVolume     relative volume ratio
     * @return true if all thresholds are met for a long entry
     */
    public boolean isValidLong(
            PeadScoreResult result,
            double epsSurprisePct,
            double revenueSurprisePct,
            double absGapPct,
            double relativeVolume) {
        return result.passes(minPeadScore)
                && epsSurprisePct     >= minEpsSurprisePct
                && revenueSurprisePct >= minRevenueSurprisePct
                && absGapPct          >= minGapPct
                && relativeVolume     >= minRelativeVolume;
    }

    /**
     * Validate a short signal: gap down with EPS + revenue miss, score passes threshold.
     * Returns false if short selling is disabled.
     *
     * @param result             pre-computed PEAD score
     * @param epsDisappointPct   EPS miss percentage (magnitude, positive value)
     * @param revDisappointPct   revenue miss percentage (magnitude, positive value)
     * @param absGapDownPct      absolute gap-down percentage (magnitude, positive value)
     * @param relativeVolume     relative volume ratio
     * @return true if all thresholds are met for a short entry
     */
    public boolean isValidShort(
            PeadScoreResult result,
            double epsDisappointPct,
            double revDisappointPct,
            double absGapDownPct,
            double relativeVolume) {
        if (!enableShortSelling) return false;
        return result.passes(minPeadScore)
                && epsDisappointPct >= minEpsSurprisePct
                && revDisappointPct >= minRevenueSurprisePct
                && absGapDownPct    >= minGapPct
                && relativeVolume   >= minRelativeVolume;
    }

    /** Returns the minimum PEAD score threshold configured. */
    public int minPeadScore() {
        return minPeadScore;
    }
}
