package org.pead.common.strategy.scoring;

/**
 * Scores revenue surprise magnitude. Max 20 points.
 *
 * Thresholds:
 *  >= 10%  -> 20 pts
 *  >= 5%   -> 17 pts
 *  >= 3%   -> 14 pts
 *  >= 2%   -> 8 pts
 *  < 2%    -> 0 pts
 */
public final class RevenueSurpriseScorer {

    public static final int MAX_SCORE = 20;
    public static final String COMPONENT = "REVENUE_SURPRISE";

    public int score(double revenueSurprisePct) {
        if (revenueSurprisePct >= 10.0) return 20;
        if (revenueSurprisePct >= 5.0)  return 17;
        if (revenueSurprisePct >= 3.0)  return 14;
        if (revenueSurprisePct >= 2.0)  return 8;
        return 0;
    }
}
