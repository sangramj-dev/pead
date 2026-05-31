package org.pead.scanner.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pead.common.config.StrategyConfig;
import org.pead.common.domain.GapDirection;
import org.pead.common.domain.TradeDirection;
import org.pead.common.event.CandidateSymbolEvent;
import org.pead.common.event.EarningsAnnouncementEvent;
import org.pead.common.event.GapDetectedEvent;
import org.pead.scanner.service.ScanCriteriaEvaluator;
import org.pead.scanner.service.WatchlistService;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import org.apache.kafka.streams.kstream.JoinWindows;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class EarningsGapJoinTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, EarningsAnnouncementEvent> earningsTopic;
    private TestInputTopic<String, GapDetectedEvent> gapTopic;
    private TestOutputTopic<String, CandidateSymbolEvent> candidateTopic;

    private static final String TICKER = "AAPL";
    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setup() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        StrategyConfig config = new StrategyConfig(
                3.0, 2.0, 5.0, 2.0, 60,
                0.01, 5, 0.02, 0.10, 0.10, 0.20,
                2.0, 3.0, false, List.of()
        );

        ScanCriteriaEvaluator evaluator = new ScanCriteriaEvaluator();
        WatchlistService watchlist = mock(WatchlistService.class);
        doNothing().when(watchlist).addToWatchlist(any());

        EarningsGapJoinTopology topology = new EarningsGapJoinTopology();
        topology.strategyConfig = config;
        topology.scanCriteriaEvaluator = evaluator;
        topology.watchlistService = watchlist;
        topology.objectMapper = mapper;

        StreamsBuilder builder = new StreamsBuilder();
        topology.buildPipeline(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-scanner");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass());

        testDriver = new TopologyTestDriver(builder.build(), props);

        JsonSerde<EarningsAnnouncementEvent> earningsSerde = new JsonSerde<>(EarningsAnnouncementEvent.class, mapper);
        JsonSerde<GapDetectedEvent> gapSerde = new JsonSerde<>(GapDetectedEvent.class, mapper);
        JsonSerde<CandidateSymbolEvent> candidateSerde = new JsonSerde<>(CandidateSymbolEvent.class, mapper);

        earningsTopic = testDriver.createInputTopic(
                "pead.earnings.announcement.v1",
                Serdes.String().serializer(),
                earningsSerde.serializer());

        gapTopic = testDriver.createInputTopic(
                "pead.market.gap-detected.v1",
                Serdes.String().serializer(),
                gapSerde.serializer());

        candidateTopic = testDriver.createOutputTopic(
                "pead.scanner.candidate.v1",
                Serdes.String().deserializer(),
                candidateSerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    @Test
    void shouldEmitCandidateWhenEarningsBeatAndGapDetected() {
        Instant now = Instant.now();

        EarningsAnnouncementEvent earnings = EarningsAnnouncementEvent.builder()
                .eventId("e1").correlationId("corr1").eventTimestamp(now.toEpochMilli())
                .ticker(TICKER).companyName("Apple Inc").announcementDate(TODAY)
                .fiscalQuarter("Q1-2025").fiscalYear(2025)
                .epsActual(2.5).epsEstimate(2.0).epsSurprisePct(25.0).epsBeat(true)
                .revenueActual(90_000_000L).revenueEstimate(85_000_000L)
                .revenueSurprisePct(5.88).revenueBeat(true).bothBeat(true).source("POLYGON")
                .build();

        GapDetectedEvent gap = GapDetectedEvent.builder()
                .eventId("g1").correlationId("corr1").eventTimestamp(now.toEpochMilli())
                .ticker(TICKER).tradeDate(TODAY)
                .prevClose(150.0).openPrice(162.0).gapPct(8.0)
                .gapDirection(GapDirection.UP).relativeVolume(3.5)
                .earningsRelated(true).earningsDate(TODAY)
                .build();

        earningsTopic.pipeInput(TICKER, earnings, now);
        gapTopic.pipeInput(TICKER, gap, now.plusMillis(100));

        var records = candidateTopic.readKeyValuesToList();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).key).isEqualTo(TICKER);
        CandidateSymbolEvent candidate = records.get(0).value;
        assertThat(candidate.getTicker()).isEqualTo(TICKER);
        assertThat(candidate.getGapPct()).isEqualTo(8.0);
        assertThat(candidate.getEpsSurprisePct()).isEqualTo(25.0);
    }

    @Test
    void shouldNotEmitCandidateWhenEpsMissed() {
        Instant now = Instant.now();

        EarningsAnnouncementEvent earnings = EarningsAnnouncementEvent.builder()
                .eventId("e2").correlationId("corr2").eventTimestamp(now.toEpochMilli())
                .ticker(TICKER).companyName("Apple Inc").announcementDate(TODAY)
                .fiscalQuarter("Q1-2025").fiscalYear(2025)
                .epsActual(1.8).epsEstimate(2.0).epsSurprisePct(-10.0).epsBeat(false)
                .revenueActual(90_000_000L).revenueEstimate(85_000_000L)
                .revenueSurprisePct(5.88).revenueBeat(true).bothBeat(false).source("POLYGON")
                .build();

        GapDetectedEvent gap = GapDetectedEvent.builder()
                .eventId("g2").correlationId("corr2").eventTimestamp(now.toEpochMilli())
                .ticker(TICKER).tradeDate(TODAY)
                .prevClose(150.0).openPrice(162.0).gapPct(8.0)
                .gapDirection(GapDirection.UP).relativeVolume(3.5)
                .earningsRelated(true).earningsDate(TODAY)
                .build();

        earningsTopic.pipeInput(TICKER, earnings, now);
        gapTopic.pipeInput(TICKER, gap, now.plusMillis(100));

        assertThat(candidateTopic.isEmpty()).isTrue();
    }

    @Test
    void shouldNotEmitCandidateWhenGapBelowThreshold() {
        Instant now = Instant.now();

        EarningsAnnouncementEvent earnings = EarningsAnnouncementEvent.builder()
                .eventId("e3").correlationId("corr3").eventTimestamp(now.toEpochMilli())
                .ticker(TICKER).companyName("Apple Inc").announcementDate(TODAY)
                .fiscalQuarter("Q1-2025").fiscalYear(2025)
                .epsActual(2.5).epsEstimate(2.0).epsSurprisePct(25.0).epsBeat(true)
                .revenueActual(90_000_000L).revenueEstimate(85_000_000L)
                .revenueSurprisePct(5.88).revenueBeat(true).bothBeat(true).source("POLYGON")
                .build();

        GapDetectedEvent gap = GapDetectedEvent.builder()
                .eventId("g3").correlationId("corr3").eventTimestamp(now.toEpochMilli())
                .ticker(TICKER).tradeDate(TODAY)
                .prevClose(150.0).openPrice(153.0).gapPct(2.0)
                .gapDirection(GapDirection.UP).relativeVolume(3.5)
                .earningsRelated(true).earningsDate(TODAY)
                .build();

        earningsTopic.pipeInput(TICKER, earnings, now);
        gapTopic.pipeInput(TICKER, gap, now.plusMillis(100));

        assertThat(candidateTopic.isEmpty()).isTrue();
    }

    @Test
    void shouldNotJoinEventsOutside24HourWindow() {
        Instant now = Instant.now();
        Instant twoDaysLater = now.plus(Duration.ofHours(25));

        EarningsAnnouncementEvent earnings = EarningsAnnouncementEvent.builder()
                .eventId("e4").correlationId("corr4").eventTimestamp(now.toEpochMilli())
                .ticker(TICKER).companyName("Apple Inc").announcementDate(TODAY)
                .fiscalQuarter("Q1-2025").fiscalYear(2025)
                .epsActual(2.5).epsEstimate(2.0).epsSurprisePct(25.0).epsBeat(true)
                .revenueActual(90_000_000L).revenueEstimate(85_000_000L)
                .revenueSurprisePct(5.88).revenueBeat(true).bothBeat(true).source("POLYGON")
                .build();

        GapDetectedEvent gap = GapDetectedEvent.builder()
                .eventId("g4").correlationId("corr4").eventTimestamp(twoDaysLater.toEpochMilli())
                .ticker(TICKER).tradeDate(TODAY.plusDays(2))
                .prevClose(150.0).openPrice(162.0).gapPct(8.0)
                .gapDirection(GapDirection.UP).relativeVolume(3.5)
                .earningsRelated(true).earningsDate(TODAY)
                .build();

        earningsTopic.pipeInput(TICKER, earnings, now);
        gapTopic.pipeInput(TICKER, gap, twoDaysLater);

        assertThat(candidateTopic.isEmpty()).isTrue();
    }
}
