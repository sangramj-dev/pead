package org.pead.strategyvalidator.scoring;

import org.springframework.stereotype.Component;

/**
 * Scores close position relative to candle high. Max 10 points.
 *
 * Rules:
 *  close >= 95% of high → 10 pts  (closed near highs, strong momentum)
 *  close >= 90% of high → 5 pts   (decent close)
 *  otherwise            → 0 pts
 */
@Component
public class ClosePositionScorer {

    public static final int MAX_SCORE = 10;
    public static final String COMPONENT = "CLOSE_POSITION";

    /**
     * @param candleClose close price of the earnings candle
     * @param candleHigh  high price of the earnings candle
     */
    public int score(double candleClose, double candleHigh) {
        if (candleHigh <= 0) return 0;
        double closePct = candleClose / candleHigh;
        if (closePct >= 0.95) return 10;
        if (closePct >= 0.90) return 5;
        return 0;
    }
}
