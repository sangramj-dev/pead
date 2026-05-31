package org.pead.scanner.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.pead.common.config.StrategyConfig;
import org.pead.common.domain.GapDirection;
import org.pead.common.domain.TradeDirection;
import org.pead.common.event.CandidateSymbolEvent;
import org.pead.common.event.EarningsAnnouncementEvent;
import org.pead.common.event.GapDetectedEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.scanner.service.ScanCriteriaEvaluator;
import org.pead.scanner.service.WatchlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Configuration
@Slf4j
public class EarningsGapJoinTopology {

    @Autowired
    StrategyConfig strategyConfig;

    @Autowired
    ScanCriteriaEvaluator scanCriteriaEvaluator;

    @Autowired
    WatchlistService watchlistService;

    @Autowired
    ObjectMapper objectMapper;

    @Bean
    public KStream<String, byte[]> buildPipeline(StreamsBuilder streamsBuilder) {

        JsonSerde<EarningsAnnouncementEvent> earningsSerde = new JsonSerde<>(EarningsAnnouncementEvent.class, objectMapper);
        JsonSerde<GapDetectedEvent> gapSerde = new JsonSerde<>(GapDetectedEvent.class, objectMapper);
        JsonSerde<CandidateSymbolEvent> candidateSerde = new JsonSerde<>(CandidateSymbolEvent.class, objectMapper);

        KStream<String, EarningsAnnouncementEvent> earningsStream = streamsBuilder
                .stream(TopicNames.EARNINGS_ANNOUNCEMENT,
                        Consumed.with(Serdes.String(), earningsSerde))
                .filter((ticker, event) -> event.isEpsBeat() && event.isRevenueBeat());

        KStream<String, GapDetectedEvent> gapStream = streamsBuilder
                .stream(TopicNames.MARKET_GAP_DETECTED,
                        Consumed.with(Serdes.String(), gapSerde));

        KStream<String, CandidateSymbolEvent> candidates = earningsStream
                .join(
                        gapStream,
                        this::buildCandidate,
                        JoinWindows.ofTimeDifferenceAndGrace(Duration.ofHours(24), Duration.ofHours(1)),
                        StreamJoined.with(Serdes.String(), earningsSerde, gapSerde))
                .filter((ticker, candidate) -> {
                    boolean passes = scanCriteriaEvaluator.meetsPreFilterCriteria(candidate, strategyConfig);
                    if (!passes) {
                        log.debug("Candidate {} filtered out by pre-filter criteria", ticker);
                    }
                    return passes;
                });

        candidates.peek((ticker, candidate) -> {
            try {
                watchlistService.addToWatchlist(candidate);
            } catch (Exception e) {
                log.warn("Failed to add {} to watchlist: {}", ticker, e.getMessage());
            }
        });

        candidates.to(TopicNames.SCANNER_CANDIDATE,
                Produced.with(Serdes.String(), candidateSerde));

        log.info("EarningsGapJoinTopology wired: {} + {} -> {}",
                TopicNames.EARNINGS_ANNOUNCEMENT,
                TopicNames.MARKET_GAP_DETECTED,
                TopicNames.SCANNER_CANDIDATE);

        return candidates.mapValues(v -> new byte[0]);
    }

    private CandidateSymbolEvent buildCandidate(EarningsAnnouncementEvent earnings, GapDetectedEvent gap) {
        TradeDirection direction = gap.getGapDirection() == GapDirection.UP
                ? TradeDirection.LONG : TradeDirection.SHORT;

        double epsSurprisePct = earnings.getEpsSurprisePct() != null ? earnings.getEpsSurprisePct() : 0.0;
        double revenueSurprisePct = earnings.getRevenueSurprisePct() != null ? earnings.getRevenueSurprisePct() : 0.0;

        return CandidateSymbolEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId(earnings.getCorrelationId())
                .eventTimestamp(System.currentTimeMillis())
                .ticker(earnings.getTicker())
                .scanDate(gap.getTradeDate())
                .direction(direction)
                .earningsEventId(earnings.getEventId())
                .gapEventId(gap.getEventId())
                .earningsDate(earnings.getAnnouncementDate())
                .epsSurprisePct(epsSurprisePct)
                .revenueSurprisePct(revenueSurprisePct)
                .gapPct(gap.getGapPct())
                .relativeVolume(gap.getRelativeVolume())
                .earningsCandleHigh(gap.getOpenPrice())
                .earningsCandleLow(gap.getPrevClose())
                .earningsCandleClose(gap.getOpenPrice())
                .preFilterScore(0)
                .build();
    }
}
