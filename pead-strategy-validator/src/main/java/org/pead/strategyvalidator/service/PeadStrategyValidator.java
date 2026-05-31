package org.pead.strategyvalidator.service;

import lombok.RequiredArgsConstructor;
import org.pead.common.config.StrategyConfig;
import org.pead.common.event.CandidateSymbolEvent;
import org.pead.strategyvalidator.scoring.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PeadStrategyValidator {

    private final EpsSurpriseScorer epsSurpriseScorer;
    private final RevenueSurpriseScorer revenueSurpriseScorer;
    private final GapStrengthScorer gapStrengthScorer;
    private final VolumeScorer volumeScorer;
    private final TrendScorer trendScorer;
    private final ClosePositionScorer closePositionScorer;
    private final StrategyConfig strategyConfig;

    public PeadScoreResult calculateScore(CandidateSymbolEvent candidate,
                                          boolean aboveEma20,
                                          boolean aboveEma50) {
        int epsScore = epsSurpriseScorer.score(candidate.getEpsSurprisePct());
        int revenueScore = revenueSurpriseScorer.score(candidate.getRevenueSurprisePct());
        int gapScore = gapStrengthScorer.score(Math.abs(candidate.getGapPct()));
        int volumeScore = volumeScorer.score(candidate.getRelativeVolume());
        int trendScore = trendScorer.score(aboveEma20, aboveEma50);
        int closeScore = closePositionScorer.score(
                candidate.getEarningsCandleClose(),
                candidate.getEarningsCandleHigh());

        int total = epsScore + revenueScore + gapScore + volumeScore + trendScore + closeScore;
        return new PeadScoreResult(total, epsScore, revenueScore, gapScore, volumeScore, trendScore, closeScore);
    }

    public boolean isValidLongSignal(CandidateSymbolEvent candidate, boolean aboveEma20, boolean aboveEma50) {
        if (candidate.getGapPct() <= 0) return false;
        if (candidate.getEpsSurprisePct() < strategyConfig.minEpsSurprisePct()) return false;
        if (candidate.getRevenueSurprisePct() < strategyConfig.minRevenueSurprisePct()) return false;
        if (candidate.getGapPct() < strategyConfig.minGapPct()) return false;
        if (candidate.getRelativeVolume() < strategyConfig.minRelativeVolume()) return false;
        if (!aboveEma20) return false;
        return true;
    }
}
