package org.pead.common.strategy.scoring;

/**
 * Scores gap strength (absolute gap percentage). Max 20 points.
 *
 * Thresholds:
 *  >= 20%  -> 20 pts
 *  >= 12%  -> 17 pts
 *  >= 8%   -> 14 pts
 *  >= 5%   -> 8 pts
 *  < 5%    -> 0 pts
 */
public final class GapStrengthScorer {

    public static final int MAX_SCORE = 20;
    public static final String COMPONENT = "GAP_STRENGTH";

    public int score(double absGapPct) {
        if (absGapPct >= 20.0) return 20;
        if (absGapPct >= 12.0) return 17;
        if (absGapPct >= 8.0)  return 14;
        if (absGapPct >= 5.0)  return 8;
        return 0;
    }
}
