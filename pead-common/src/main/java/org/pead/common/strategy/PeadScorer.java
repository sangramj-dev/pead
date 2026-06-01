package org.pead.common.strategy;

import org.pead.common.strategy.scoring.ClosePositionScorer;
import org.pead.common.strategy.scoring.EpsSurpriseScorer;
import org.pead.common.strategy.scoring.GapStrengthScorer;
import org.pead.common.strategy.scoring.PeadScoreResult;
import org.pead.common.strategy.scoring.RevenueSurpriseScorer;
import org.pead.common.strategy.scoring.TrendScorer;
import org.pead.common.strategy.scoring.VolumeScorer;

/**
 * Orchestrates all PEAD scoring components to produce a composite score (max 100).
 * Pure Java — no Spring dependencies; instantiable with {@code new}.
 */
public final class PeadScorer {

    private final EpsSurpriseScorer     epsScorer          = new EpsSurpriseScorer();
    private final RevenueSurpriseScorer revenueScorer      = new RevenueSurpriseScorer();
    private final GapStrengthScorer     gapScorer          = new GapStrengthScorer();
    private final VolumeScorer          volumeScorer       = new VolumeScorer();
    private final TrendScorer           trendScorer        = new TrendScorer();
    private final ClosePositionScorer   closePositionScorer = new ClosePositionScorer();

    /**
     * Compute the PEAD composite score for a candidate signal.
     *
     * @param epsSurprisePct     EPS beat percentage (e.g. 15.0 for 15%)
     * @param revenueSurprisePct revenue beat percentage
     * @param absGapPct          absolute gap percentage (always positive)
     * @param relativeVolume     today's volume divided by 20-day average
     * @param aboveEma20         true if price closed above EMA-20
     * @param aboveEma50         true if price closed above EMA-50
     * @param candleClose        earnings candle close price
     * @param candleHigh         earnings candle high price
     * @return {@link PeadScoreResult} with total and per-component scores
     */
    public PeadScoreResult score(
            double epsSurprisePct,
            double revenueSurprisePct,
            double absGapPct,
            double relativeVolume,
            boolean aboveEma20,
            boolean aboveEma50,
            double candleClose,
            double candleHigh) {

        int epsScore          = epsScorer.score(epsSurprisePct);
        int revenueScore      = revenueScorer.score(revenueSurprisePct);
        int gapScore          = gapScorer.score(absGapPct);
        int volumeScore       = volumeScorer.score(relativeVolume);
        int trendScore        = trendScorer.score(aboveEma20, aboveEma50);
        int closePositionScore = closePositionScorer.score(candleClose, candleHigh);

        int totalScore = epsScore + revenueScore + gapScore + volumeScore + trendScore + closePositionScore;

        return new PeadScoreResult(
                totalScore,
                epsScore,
                revenueScore,
                gapScore,
                volumeScore,
                trendScore,
                closePositionScore
        );
    }
}
