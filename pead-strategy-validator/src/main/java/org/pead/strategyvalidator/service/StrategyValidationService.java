package org.pead.strategyvalidator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.config.StrategyConfig;
import org.pead.common.domain.SignalStatus;
import org.pead.common.domain.TradeDirection;
import org.pead.common.event.CandidateSymbolEvent;
import org.pead.strategyvalidator.client.MarketDataClient;
import org.pead.strategyvalidator.client.dto.DailyIndicatorDto;
import org.pead.strategyvalidator.domain.RejectedCandidate;
import org.pead.strategyvalidator.domain.ScoringDetail;
import org.pead.strategyvalidator.domain.ValidatedSignal;
import org.pead.strategyvalidator.kafka.producer.ValidatedSignalEventPublisher;
import org.pead.strategyvalidator.repository.RejectedCandidateRepository;
import org.pead.strategyvalidator.repository.ScoringDetailRepository;
import org.pead.strategyvalidator.repository.ValidatedSignalRepository;
import org.pead.strategyvalidator.scoring.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyValidationService {

    private final PeadStrategyValidator peadStrategyValidator;
    private final TradeSetupCalculator tradeSetupCalculator;
    private final ValidatedSignalRepository signalRepository;
    private final ScoringDetailRepository scoringDetailRepository;
    private final RejectedCandidateRepository rejectedCandidateRepository;
    private final ValidatedSignalEventPublisher validatedSignalEventPublisher;
    private final MarketDataClient marketDataClient;
    private final StrategyConfig strategyConfig;
    private final ObjectMapper objectMapper;

    @Transactional
    public void validateCandidate(CandidateSymbolEvent candidate) {
        String ticker = candidate.getTicker();
        log.info("Validating candidate: ticker={}, gap={}%, rvol={}",
                ticker, candidate.getGapPct(), candidate.getRelativeVolume());

        DailyIndicatorDto indicator = marketDataClient.getLatestIndicator(ticker).orElse(null);
        boolean aboveEma20 = indicator != null && Boolean.TRUE.equals(indicator.aboveEma20());
        boolean aboveEma50 = indicator != null && Boolean.TRUE.equals(indicator.aboveEma50());
        BigDecimal ema20 = indicator != null ? indicator.ema20() : null;
        BigDecimal ema50 = indicator != null ? indicator.ema50() : null;

        PeadScoreResult scoreResult = peadStrategyValidator.calculateScore(candidate, aboveEma20, aboveEma50);
        boolean qualitative = peadStrategyValidator.isValidLongSignal(candidate, aboveEma20, aboveEma50);
        boolean passes = qualitative && scoreResult.passes(strategyConfig.minPeadScore());

        log.info("Candidate {}: score={}, qualitative={}, passes={}",
                ticker, scoreResult.totalScore(), qualitative, passes);

        if (passes) {
            TradeSetupCalculator.TradeSetup setup = tradeSetupCalculator.calculate(
                    candidate.getEarningsCandleHigh(),
                    candidate.getEarningsCandleLow());

            ValidatedSignal signal = buildValidatedSignal(candidate, scoreResult, setup, ema20, ema50, aboveEma20, aboveEma50);
            signalRepository.save(signal);
            saveScoringDetails(signal.getSignalId(), scoreResult, candidate);
            validatedSignalEventPublisher.publish(signal);

            log.info("Signal VALIDATED: ticker={}, score={}, entry={}, stop={}, target1={}, target2={}",
                    ticker, scoreResult.totalScore(),
                    setup.entryPrice(), setup.stopLoss(), setup.target1(), setup.target2());
        } else {
            List<String> reasons = buildRejectionReasons(candidate, scoreResult, qualitative, aboveEma20, aboveEma50);
            RejectedCandidate rejected = buildRejectedCandidate(candidate, reasons, scoreResult);
            rejectedCandidateRepository.save(rejected);

            log.info("Candidate REJECTED: ticker={}, reasons={}", ticker, reasons);
        }
    }

    private ValidatedSignal buildValidatedSignal(CandidateSymbolEvent c, PeadScoreResult score,
                                                  TradeSetupCalculator.TradeSetup setup,
                                                  BigDecimal ema20, BigDecimal ema50,
                                                  boolean aboveEma20, boolean aboveEma50) {
        boolean closeNearHigh = c.getEarningsCandleHigh() > 0
                && (c.getEarningsCandleClose() / c.getEarningsCandleHigh()) >= 0.95;

        ValidatedSignal s = new ValidatedSignal();
        s.setTicker(c.getTicker());
        s.setDirection(TradeDirection.LONG);
        s.setSignalDate(LocalDate.now());
        s.setEarningsDate(c.getEarningsDate() != null ? c.getEarningsDate() : LocalDate.now());
        s.setPeadScore(score.totalScore());
        s.setEntryPrice(setup.entryPrice());
        s.setStopLoss(setup.stopLoss());
        s.setTarget1(setup.target1());
        s.setTarget2(setup.target2());
        s.setRiskRewardRatio(setup.riskRewardRatio());
        s.setEpsSurprisePct(c.getEpsSurprisePct());
        s.setRevenueSurprisePct(c.getRevenueSurprisePct());
        s.setGapPct(c.getGapPct());
        s.setRelVolume(c.getRelativeVolume());
        s.setEma20(ema20);
        s.setEma50(ema50);
        s.setAboveEma20(aboveEma20);
        s.setAboveEma50(aboveEma50);
        s.setCloseNearHigh(closeNearHigh);
        s.setStatus(SignalStatus.ACTIVE);
        s.setExpiryDate(LocalDate.now().plusDays(2));
        s.setCorrelationId(c.getCorrelationId());
        return s;
    }

    private void saveScoringDetails(java.util.UUID signalId, PeadScoreResult score, CandidateSymbolEvent c) {
        List<ScoringDetail> details = List.of(
                detail(signalId, EpsSurpriseScorer.COMPONENT,   c.getEpsSurprisePct(),   score.epsScore(),           EpsSurpriseScorer.MAX_SCORE),
                detail(signalId, RevenueSurpriseScorer.COMPONENT, c.getRevenueSurprisePct(), score.revenueScore(),   RevenueSurpriseScorer.MAX_SCORE),
                detail(signalId, GapStrengthScorer.COMPONENT,   Math.abs(c.getGapPct()), score.gapScore(),           GapStrengthScorer.MAX_SCORE),
                detail(signalId, VolumeScorer.COMPONENT,         c.getRelativeVolume(),   score.volumeScore(),       VolumeScorer.MAX_SCORE),
                detail(signalId, TrendScorer.COMPONENT,          null,                    score.trendScore(),        TrendScorer.MAX_SCORE),
                detail(signalId, ClosePositionScorer.COMPONENT,  c.getEarningsCandleClose() / c.getEarningsCandleHigh() * 100, score.closePositionScore(), ClosePositionScorer.MAX_SCORE)
        );
        scoringDetailRepository.saveAll(details);
    }

    private ScoringDetail detail(java.util.UUID signalId, String component, Double rawValue, int score, int weight) {
        ScoringDetail d = new ScoringDetail();
        d.setSignalId(signalId);
        d.setComponent(component);
        d.setRawValue(rawValue);
        d.setScore(score);
        d.setWeight(weight);
        d.setWeightedScore(score);
        return d;
    }

    private List<String> buildRejectionReasons(CandidateSymbolEvent c, PeadScoreResult score,
                                                boolean qualitative, boolean aboveEma20, boolean aboveEma50) {
        List<String> reasons = new ArrayList<>();
        if (c.getGapPct() <= 0) reasons.add("NOT_A_GAP_UP");
        if (c.getEpsSurprisePct() < strategyConfig.minEpsSurprisePct())
            reasons.add("INSUFFICIENT_EPS_SURPRISE_" + String.format("%.1f", c.getEpsSurprisePct()));
        if (c.getRevenueSurprisePct() < strategyConfig.minRevenueSurprisePct())
            reasons.add("INSUFFICIENT_REVENUE_SURPRISE_" + String.format("%.1f", c.getRevenueSurprisePct()));
        if (c.getGapPct() < strategyConfig.minGapPct())
            reasons.add("INSUFFICIENT_GAP_" + String.format("%.1f", c.getGapPct()));
        if (c.getRelativeVolume() < strategyConfig.minRelativeVolume())
            reasons.add("LOW_VOLUME_" + String.format("%.1f", c.getRelativeVolume()) + "x");
        if (!aboveEma20) reasons.add("BELOW_EMA20");
        if (!score.passes(strategyConfig.minPeadScore()))
            reasons.add("LOW_PEAD_SCORE_" + score.totalScore() + "_MIN_" + strategyConfig.minPeadScore());
        if (reasons.isEmpty()) reasons.add("UNKNOWN");
        return reasons;
    }

    private RejectedCandidate buildRejectedCandidate(CandidateSymbolEvent c,
                                                      List<String> reasons,
                                                      PeadScoreResult score) {
        RejectedCandidate r = new RejectedCandidate();
        r.setTicker(c.getTicker());
        r.setScanDate(LocalDate.now());

        try {
            r.setRejectionReasons(objectMapper.writeValueAsString(reasons));

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("epsSurprisePct", c.getEpsSurprisePct());
            metrics.put("revenueSurprisePct", c.getRevenueSurprisePct());
            metrics.put("gapPct", c.getGapPct());
            metrics.put("relativeVolume", c.getRelativeVolume());
            metrics.put("earningsCandleHigh", c.getEarningsCandleHigh());
            metrics.put("earningsCandleLow", c.getEarningsCandleLow());
            metrics.put("earningsCandleClose", c.getEarningsCandleClose());
            metrics.put("peadScore", score.totalScore());
            r.setRawMetrics(objectMapper.writeValueAsString(metrics));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize rejection data for {}", c.getTicker(), e);
            r.setRejectionReasons("[]");
            r.setRawMetrics("{}");
        }

        return r;
    }
}
