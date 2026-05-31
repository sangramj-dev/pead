package org.pead.strategyvalidator.scoring;

import org.springframework.stereotype.Component;

/**
 * Scores EPS surprise magnitude. Max 25 points.
 *
 * Thresholds:
 *  >= 20%  → 25 pts
 *  >= 10%  → 21 pts
 *  >= 5%   → 17 pts
 *  >= 3%   → 10 pts
 *  < 3%    → 0 pts
 */
@Component
public class EpsSurpriseScorer {

    public static final int MAX_SCORE = 25;
    public static final String COMPONENT = "EPS_SURPRISE";

    public int score(double epsSurprisePct) {
        if (epsSurprisePct >= 20.0) return 25;
        if (epsSurprisePct >= 10.0) return 21;
        if (epsSurprisePct >= 5.0)  return 17;
        if (epsSurprisePct >= 3.0)  return 10;
        return 0;
    }
}
