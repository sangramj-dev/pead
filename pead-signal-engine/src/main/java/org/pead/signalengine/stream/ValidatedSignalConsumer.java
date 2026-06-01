package org.pead.signalengine.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pead.common.event.ValidatedSignalEvent;
import org.pead.common.kafka.TopicNames;
import org.pead.signalengine.service.PendingSignalStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens for validated signal events and stores them
 * in the pending signal store for breakout detection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidatedSignalConsumer {

    private final PendingSignalStore pendingSignalStore;

    @KafkaListener(
            topics = TopicNames.STRATEGY_VALIDATED_SIGNAL,
            groupId = "signal-engine-group",
            properties = {
                    "spring.json.value.default.type=org.pead.common.event.ValidatedSignalEvent"
            }
    )
    public void onValidatedSignal(ValidatedSignalEvent event) {
        log.info("Received validated signal: ticker={}, signalId={}, direction={}, entryPrice={}",
                event.getTicker(), event.getSignalId(), event.getDirection(), event.getEntryPrice());

        pendingSignalStore.store(event);
    }
}
