package org.pead.signalengine.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pead.common.config.StrategyConfig;
import org.pead.common.domain.TradeDirection;
import org.pead.common.event.EntrySignalEvent;
import org.pead.common.event.PriceBarEvent;
import org.pead.common.event.ValidatedSignalEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.signalengine.config.SignalEngineConfig;
import org.pead.signalengine.service.PendingSignalStore;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceBarProcessorTest {

    @Mock
    private PendingSignalStore pendingSignalStore;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PriceBarProcessor processor;

    @BeforeEach
    void setUp() {
        StrategyConfig strategyConfig = new StrategyConfig(
                3.0,   // minEpsSurprisePct
                2.0,   // minRevenueSurprisePct
                5.0,   // minGapPct
                2.0,   // minRelativeVolume
                60,    // minPeadScore
                0.01,  // maxRiskPctPerTrade
                5,     // maxConcurrentPositions
                0.02,  // dailyLossLimitPct
                0.10,  // maxDrawdownPct
                0.10,  // maxPositionSizePct
                0.20,  // maxSectorConcentrationPct
                2.0,   // profitTarget1R
                3.0,   // profitTarget2R
                false, // enableShortSelling
                List.of() // excludedSectors
        );

        SignalEngineConfig signalEngineConfig = new SignalEngineConfig(1_000_000, 5);

        processor = new PriceBarProcessor(
                pendingSignalStore,
                kafkaTemplate,
                strategyConfig,
                signalEngineConfig
        );
    }

    @Test
    @DisplayName("Should publish EntrySignalEvent when bar high >= entry price for LONG signal")
    void shouldPublishEntryWhenBreakoutDetectedForLong() {
        // Given
        String ticker = "RELIANCE.NS";
        ValidatedSignalEvent signal = ValidatedSignalEvent.builder()
                .eventId("evt-1")
                .correlationId("corr-1")
                .eventTimestamp(System.currentTimeMillis())
                .signalId("sig-1")
                .ticker(ticker)
                .direction(TradeDirection.LONG)
                .signalDate(LocalDate.now())
                .earningsDate(LocalDate.now().minusDays(1))
                .peadScore(75)
                .entryPrice(2500.0)
                .stopLoss(2400.0)
                .target1(2700.0)
                .target2(2800.0)
                .riskRewardRatio(2.0)
                .epsSurprisePct(5.0)
                .revenueSurprisePct(3.0)
                .gapPct(6.0)
                .relativeVolume(3.0)
                .ema20(2450.0)
                .ema50(2350.0)
                .aboveEma20(true)
                .aboveEma50(true)
                .closeNearHigh(true)
                .expiryDate(LocalDate.now().plusDays(5))
                .build();

        PriceBarEvent bar = PriceBarEvent.builder()
                .eventId("bar-1")
                .correlationId("corr-bar-1")
                .eventTimestamp(System.currentTimeMillis())
                .ticker(ticker)
                .barDate(LocalDate.now())
                .timeframe("1D")
                .open(2480.0)
                .high(2520.0)  // >= entryPrice of 2500
                .low(2470.0)
                .close(2510.0)
                .volume(1000000L)
                .build();

        when(pendingSignalStore.getByTicker(ticker)).thenReturn(List.of(signal));

        // When
        processor.onPriceBar(bar);

        // Then
        ArgumentCaptor<EntrySignalEvent> captor = ArgumentCaptor.forClass(EntrySignalEvent.class);
        verify(kafkaTemplate).send(eq(TopicNames.SIGNAL_ENTRY), eq(ticker), captor.capture());

        EntrySignalEvent published = captor.getValue();
        assertThat(published.getTicker()).isEqualTo(ticker);
        assertThat(published.getSignalId()).isEqualTo("sig-1");
        assertThat(published.getDirection()).isEqualTo("LONG");
        assertThat(published.getEntryPrice().doubleValue()).isEqualTo(2500.0);
        assertThat(published.getStopLoss().doubleValue()).isEqualTo(2400.0);
        assertThat(published.getQuantity()).isGreaterThan(0);
        assertThat(published.getPeadScore()).isEqualTo(75);

        verify(pendingSignalStore).remove(ticker, "sig-1");
    }

    @Test
    @DisplayName("Should NOT publish when bar high < entry price for LONG signal")
    void shouldNotPublishWhenNoBreakoutForLong() {
        // Given
        String ticker = "TCS.NS";
        ValidatedSignalEvent signal = ValidatedSignalEvent.builder()
                .signalId("sig-2")
                .ticker(ticker)
                .direction(TradeDirection.LONG)
                .entryPrice(3500.0)
                .stopLoss(3400.0)
                .target1(3700.0)
                .target2(3800.0)
                .peadScore(70)
                .expiryDate(LocalDate.now().plusDays(5))
                .build();

        PriceBarEvent bar = PriceBarEvent.builder()
                .ticker(ticker)
                .barDate(LocalDate.now())
                .timeframe("1D")
                .open(3420.0)
                .high(3480.0)  // < entryPrice of 3500
                .low(3410.0)
                .close(3460.0)
                .volume(500000L)
                .build();

        when(pendingSignalStore.getByTicker(ticker)).thenReturn(List.of(signal));

        // When
        processor.onPriceBar(bar);

        // Then
        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(pendingSignalStore, never()).remove(any(), any());
    }

    @Test
    @DisplayName("Should NOT publish when no pending signal exists for ticker")
    void shouldNotPublishWhenNoPendingSignal() {
        // Given
        String ticker = "INFY.NS";
        PriceBarEvent bar = PriceBarEvent.builder()
                .ticker(ticker)
                .barDate(LocalDate.now())
                .timeframe("1D")
                .open(1500.0)
                .high(1550.0)
                .low(1490.0)
                .close(1540.0)
                .volume(800000L)
                .build();

        when(pendingSignalStore.getByTicker(ticker)).thenReturn(List.of());

        // When
        processor.onPriceBar(bar);

        // Then
        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(pendingSignalStore, never()).remove(any(), any());
    }

    @Test
    @DisplayName("Should publish EntrySignalEvent when bar low <= entry price for SHORT signal")
    void shouldPublishEntryWhenBreakoutDetectedForShort() {
        // Given
        String ticker = "HDFCBANK.NS";
        ValidatedSignalEvent signal = ValidatedSignalEvent.builder()
                .eventId("evt-3")
                .correlationId("corr-3")
                .eventTimestamp(System.currentTimeMillis())
                .signalId("sig-3")
                .ticker(ticker)
                .direction(TradeDirection.SHORT)
                .signalDate(LocalDate.now())
                .earningsDate(LocalDate.now().minusDays(1))
                .peadScore(80)
                .entryPrice(1600.0)
                .stopLoss(1700.0)
                .target1(1450.0)
                .target2(1350.0)
                .riskRewardRatio(1.5)
                .epsSurprisePct(-4.0)
                .revenueSurprisePct(-3.0)
                .gapPct(-7.0)
                .relativeVolume(4.0)
                .ema20(1650.0)
                .ema50(1680.0)
                .aboveEma20(false)
                .aboveEma50(false)
                .closeNearHigh(false)
                .expiryDate(LocalDate.now().plusDays(5))
                .build();

        PriceBarEvent bar = PriceBarEvent.builder()
                .eventId("bar-3")
                .correlationId("corr-bar-3")
                .eventTimestamp(System.currentTimeMillis())
                .ticker(ticker)
                .barDate(LocalDate.now())
                .timeframe("1D")
                .open(1620.0)
                .high(1630.0)
                .low(1590.0)  // <= entryPrice of 1600
                .close(1595.0)
                .volume(1200000L)
                .build();

        when(pendingSignalStore.getByTicker(ticker)).thenReturn(List.of(signal));

        // When
        processor.onPriceBar(bar);

        // Then
        ArgumentCaptor<EntrySignalEvent> captor = ArgumentCaptor.forClass(EntrySignalEvent.class);
        verify(kafkaTemplate).send(eq(TopicNames.SIGNAL_ENTRY), eq(ticker), captor.capture());

        EntrySignalEvent published = captor.getValue();
        assertThat(published.getTicker()).isEqualTo(ticker);
        assertThat(published.getSignalId()).isEqualTo("sig-3");
        assertThat(published.getDirection()).isEqualTo("SHORT");
        assertThat(published.getEntryPrice().doubleValue()).isEqualTo(1600.0);
        assertThat(published.getQuantity()).isGreaterThan(0);

        verify(pendingSignalStore).remove(ticker, "sig-3");
    }
}
