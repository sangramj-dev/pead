package org.pead.common.strategy.scoring;

/**
 * Scores EMA trend alignment. Max 10 points.
 *
 * Rules:
 *  Above EMA20 AND EMA50 -> 10 pts  (strong uptrend)
 *  Above EMA20 only      -> 5 pts   (short-term uptrend)
 *  Neither               -> 0 pts
 */
public final class TrendScorer {

    public static final int MAX_SCORE = 10;
    public static final String COMPONENT = "TREND_ALIGNMENT";

    public int score(boolean aboveEma20, boolean aboveEma50) {
        if (aboveEma20 && aboveEma50) return 10;
        if (aboveEma20) return 5;
        return 0;
    }
}
