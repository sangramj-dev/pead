package org.pead.strategyvalidator.scoring;

/**
 * Immutable result of PEAD score calculation (max 100 points).
 *
 * Components:
 *  EPS surprise:      max 25
 *  Revenue surprise:  max 20
 *  Gap strength:      max 20
 *  Volume:            max 15
 *  Trend alignment:   max 10
 *  Close position:    max 10
 */
public record PeadScoreResult(
        int totalScore,
        int epsScore,
        int revenueScore,
        int gapScore,
        int volumeScore,
        int trendScore,
        int closePositionScore
) {
    public boolean passes(int minScore) {
        return totalScore >= minScore;
    }
}
