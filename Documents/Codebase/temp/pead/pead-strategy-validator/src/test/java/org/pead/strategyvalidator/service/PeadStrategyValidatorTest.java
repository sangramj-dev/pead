package org.pead.strategyvalidator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pead.common.config.StrategyConfig;
import org.pead.common.domain.TradeDirection;
import org.pead.common.event.CandidateSymbolEvent;
import org.pead.strategyvalidator.scoring.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeadStrategyValidatorTest {

    private PeadStrategyValidator validator;
    private StrategyConfig config;

    @BeforeEach
    void setup() {
        config = new StrategyConfig(
                3.0, 2.0, 5.0, 2.0, 60,
                0.01, 5, 0.02, 0.10, 0.10, 0.20,
                2.0, 3.0, false, List.of()
        );
        validator = new PeadStrategyValidator(
                new EpsSurpriseScorer(),
                new RevenueSurpriseScorer(),
                new GapStrengthScorer(),
                new VolumeScorer(),
                new TrendScorer(),
                new ClosePositionScorer(),
                config
        );
    }

    @Test
    void shouldScoreStrongCandidate() {
        CandidateSymbolEvent candidate = buildCandidate(
                "AAPL", 25.0, 8.0, 8.0, 3.5, 160.0, 148.0, 158.0
        );

        PeadScoreResult result = validator.calculateScore(candidate, true, true);

        assertThat(result.epsScore()).isEqualTo(25);
        assertThat(result.revenueScore()).isEqualTo(17);
        assertThat(result.gapScore()).isEqualTo(8);
        assertThat(result.volumeScore()).isEqualTo(10);
        assertThat(result.trendScore()).isEqualTo(10);
        assertThat(result.closePositionScore()).isEqualTo(10);
        assertThat(result.totalScore()).isEqualTo(80);
        assertThat(result.passes(60)).isTrue();
    }

    @Test
    void shouldFailValidationWhenBelowMinEpsSurprise() {
        CandidateSymbolEvent candidate = buildCandidate(
                "MSFT", 1.0, 2.0, 6.0, 2.5, 300.0, 280.0, 295.0
        );

        boolean valid = validator.isValidLongSignal(candidate, true, true);

        assertThat(valid).isFalse();
    }

    @Test
    void shouldFailValidationWhenBelowMinGap() {
        CandidateSymbolEvent candidate = buildCandidate(
                "GOOGL", 10.0, 5.0, 3.0, 2.5, 140.0, 132.0, 138.0
        );

        boolean valid = validator.isValidLongSignal(candidate, true, true);

        assertThat(valid).isFalse();
    }

    @Test
    void shouldFailValidationWhenBelowEma20() {
        CandidateSymbolEvent candidate = buildCandidate(
                "META", 10.0, 5.0, 6.0, 3.0, 500.0, 465.0, 495.0
        );

        boolean valid = validator.isValidLongSignal(candidate, false, false);

        assertThat(valid).isFalse();
    }

    @Test
    void shouldPassValidationWithStrongSignal() {
        CandidateSymbolEvent candidate = buildCandidate(
                "NVDA", 30.0, 10.0, 9.0, 5.0, 900.0, 820.0, 890.0
        );

        boolean valid = validator.isValidLongSignal(candidate, true, true);

        assertThat(valid).isTrue();
    }

    @Test
    void shouldScoreBorderlineCandidate() {
        CandidateSymbolEvent candidate = buildCandidate(
                "AMD", 3.5, 2.5, 5.2, 2.1, 100.0, 92.0, 94.0
        );

        PeadScoreResult result = validator.calculateScore(candidate, true, false);

        assertThat(result.epsScore()).isEqualTo(10);
        assertThat(result.revenueScore()).isEqualTo(8);
        assertThat(result.gapScore()).isEqualTo(8);
        assertThat(result.volumeScore()).isEqualTo(6);
        assertThat(result.trendScore()).isEqualTo(5);
        assertThat(result.closePositionScore()).isEqualTo(5);
        assertThat(result.totalScore()).isEqualTo(42);
        assertThat(result.passes(60)).isFalse();
    }

    private CandidateSymbolEvent buildCandidate(String ticker, double epsSurprisePct,
                                                  double revenueSurprisePct, double gapPct,
                                                  double relVol, double high, double low, double close) {
        return CandidateSymbolEvent.builder()
                .eventId("test-event")
                .correlationId("test-corr")
                .eventTimestamp(System.currentTimeMillis())
                .ticker(ticker)
                .scanDate(LocalDate.now())
                .direction(TradeDirection.LONG)
                .earningsEventId("e1")
                .gapEventId("g1")
                .earningsDate(LocalDate.now())
                .epsSurprisePct(epsSurprisePct)
                .revenueSurprisePct(revenueSurprisePct)
                .gapPct(gapPct)
                .relativeVolume(relVol)
                .earningsCandleHigh(high)
                .earningsCandleLow(low)
                .earningsCandleClose(close)
                .preFilterScore(0)
                .build();
    }
}
