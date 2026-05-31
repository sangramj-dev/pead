package org.pead.strategyvalidator.scoring;

import org.springframework.stereotype.Component;

/**
 * Scores relative volume (today's volume vs. 20-day average). Max 15 points.
 *
 * Thresholds:
 *  >= 10x  → 15 pts
 *  >= 5x   → 13 pts
 *  >= 3x   → 10 pts
 *  >= 2x   → 6 pts
 *  < 2x    → 0 pts
 */
@Component
public class VolumeScorer {

    public static final int MAX_SCORE = 15;
    public static final String COMPONENT = "VOLUME";

    public int score(double relativeVolume) {
        if (relativeVolume >= 10.0) return 15;
        if (relativeVolume >= 5.0)  return 13;
        if (relativeVolume >= 3.0)  return 10;
        if (relativeVolume >= 2.0)  return 6;
        return 0;
    }
}
