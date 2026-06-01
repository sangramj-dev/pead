package org.pead.signalengine.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.config.StrategyConfig;
import org.pead.common.domain.TradeDirection;
import org.pead.common.event.EntrySignalEvent;
import org.pead.common.event.PriceBarEvent;
import org.pead.common.event.ValidatedSignalEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.common.strategy.PositionSizer;
import org.pead.signalengine.config.SignalEngineConfig;
import org.pead.signalengine.service.PendingSignalStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Kafka consumer that processes price bar events and detects breakout entries.
 * For each incoming price bar, checks if any pending validated signal for that ticker
 * has been triggered (price crossed the entry level).
 *
 * <p>Breakout detection logic:
 * <ul>
 *   <li>LONG: bar high >= signal entry price</li>
 *   <li>SHORT: bar low <= signal entry price</li>
 * </ul>
 *
 * <p>On breakout detection, calculates position size via {@link PositionSizer},
 * emits an {@link EntrySignalEvent}, and removes the triggered signal from the store.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceBarProcessor {

    private final PendingSignalStore pendingSignalStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StrategyConfig strategyConfig;
    private final SignalEngineConfig signalEngineConfig;

    @KafkaListener(
            topics = TopicNames.MARKET_PRICE_BAR,
            groupId = "signal-engine-pricebar-group",
            properties = {
                    "spring.json.value.default.type=org.pead.common.event.PriceBarEvent"
            }
    )
    public void onPriceBar(PriceBarEvent bar) {
        String ticker = bar.getTicker();
        List<ValidatedSignalEvent> pendingSignals = pendingSignalStore.getByTicker(ticker);

        if (pendingSignals.isEmpty()) {
            return;
        }

        for (ValidatedSignalEvent signal : pendingSignals) {
            if (isBreakoutTriggered(bar, signal)) {
                log.info("Breakout detected: ticker={}, signalId={}, direction={}, entryPrice={}, barHigh={}, barLow={}",
                        ticker, signal.getSignalId(), signal.getDirection(),
                        signal.getEntryPrice(), bar.getHigh(), bar.getLow());

                EntrySignalEvent entryEvent = buildEntrySignalEvent(signal);
                kafkaTemplate.send(TopicNames.SIGNAL_ENTRY, ticker, entryEvent);

                log.info("Published entry signal: ticker={}, signalId={}, quantity={}",
                        ticker, signal.getSignalId(), entryEvent.getQuantity());

                pendingSignalStore.remove(ticker, signal.getSignalId());
            }
        }
    }

    private boolean isBreakoutTriggered(PriceBarEvent bar, ValidatedSignalEvent signal) {
        if (signal.getDirection() == TradeDirection.LONG) {
            return bar.getHigh() >= signal.getEntryPrice();
        } else if (signal.getDirection() == TradeDirection.SHORT) {
            return bar.getLow() <= signal.getEntryPrice();
        }
        return false;
    }

    private EntrySignalEvent buildEntrySignalEvent(ValidatedSignalEvent signal) {
        PositionSizer sizer = new PositionSizer(
                strategyConfig.maxRiskPctPerTrade(),
                strategyConfig.maxConcurrentPositions(),
                strategyConfig.maxPositionSizePct()
        );

        double accountEquity = signalEngineConfig.defaultAccountEquity();
        int quantity = sizer.calculateShares(accountEquity, signal.getEntryPrice(), signal.getStopLoss());

        return EntrySignalEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId(signal.getCorrelationId())
                .eventTimestamp(System.currentTimeMillis())
                .ticker(signal.getTicker())
                .signalId(signal.getSignalId())
                .direction(signal.getDirection().name())
                .entryPrice(BigDecimal.valueOf(signal.getEntryPrice()))
                .stopLoss(BigDecimal.valueOf(signal.getStopLoss()))
                .target1(BigDecimal.valueOf(signal.getTarget1()))
                .target2(BigDecimal.valueOf(signal.getTarget2()))
                .quantity(quantity)
                .peadScore(signal.getPeadScore())
                .build();
    }
}
